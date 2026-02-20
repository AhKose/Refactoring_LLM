/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tools.descartes.teastore.recommender.servlet;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.descartes.teastore.recommender.algorithm.RecommenderSelector;
import tools.descartes.teastore.registryclient.Service;
import tools.descartes.teastore.registryclient.loadbalancers.LoadBalancerTimeoutException;
import tools.descartes.teastore.registryclient.loadbalancers.ServiceLoadBalancer;
import tools.descartes.teastore.registryclient.rest.LoadBalancedCRUDOperations;
import tools.descartes.teastore.registryclient.util.NotFoundException;
import tools.descartes.teastore.entities.Order;
import tools.descartes.teastore.entities.OrderItem;

/**
 * This class organizes the communication with the other services and
 * synchronizes on startup and training.
 *
 * @author Johannes Grohmann
 *
 */
public final class TrainingSynchronizer {

	/**
	 * This value signals that the maximum training time is not known.
	 */
	public static final long DEFAULT_MAX_TIME_VALUE = Long.MIN_VALUE;

	// Longest wait period before querying the persistence again if it is finished
	// creating entries
	private static final int PERSISTENCE_CREATION_MAX_WAIT_TIME = 120000;
	// Wait time in ms before checking again for an existing persistence service
	private static final List<Integer> PERSISTENCE_CREATION_WAIT_TIME = Arrays.asList(1000, 2000, 5000, 10000, 30000,
			60000);

	private static TrainingSynchronizer instance;

	private boolean isReady = false;

	/**
	 * @return the isReady
	 */
	public boolean isReady() {
		return isReady;
	}

	/**
	 * @param isReady
	 *            the isReady to set
	 */
	public void setReady(boolean isReady) {
		this.isReady = isReady;
	}

	private TrainingSynchronizer() {

	}

	/**
	 * Returns the instance for this singleton.
	 *
	 * @return An instance of {@link TrainingSynchronizer}
	 */
	public static synchronized TrainingSynchronizer getInstance() {
		if (instance == null) {
			instance = new TrainingSynchronizer();
		}
		return instance;
	}

	private static final Logger LOG = LoggerFactory.getLogger(TrainingSynchronizer.class);

	/**
	 * The maximum considered time in milliseconds. DEFAULT_MAX_TIME_VALUE signals
	 * no entry, e.g. all orders are used for training.
	 */
	private long maxTime = DEFAULT_MAX_TIME_VALUE;

	/**
	 * @return the maxTime
	 */
	public long getMaxTime() {
		return maxTime;
	}

	/**
	 * @param maxTime
	 *            the maxTime to set
	 */
	public void setMaxTime(String maxTime) {
		setMaxTime(toMillis(maxTime));
	}

	/**
	 * @param maxTime
	 *            the maxTime to set
	 */
	public void setMaxTime(long maxTime) {
		this.maxTime = maxTime;
	}

	private void waitForPersistence() {
		// We have to wait for the database that all entries are created before
		// generating images (which queries persistence). Yes we want to wait forever in
		// case the persistence is
		// not answering.
		Iterator<Integer> waitTimes = PERSISTENCE_CREATION_WAIT_TIME.iterator();
		while (true) {
			Response result = null;
			try {
				result = ServiceLoadBalancer.loadBalanceRESTOperation(Service.PERSISTENCE, "generatedb", String.class,
						client -> client.getService().path(client.getApplicationURI()).path(client.getEndpointURI())
								.path("finished").request().get());

								if (result != null && Boolean.parseBoolean(result.readEntity(String.class))) {
									break;
				}
			} catch (NullPointerException | NotFoundException | LoadBalancerTimeoutException e) {
				// continue waiting as usual
			} finally {
				if (result != null) {
					result.close();
				}
			}
			try {
				int nextWaitTime;
				if (waitTimes.hasNext()) {
					nextWaitTime = waitTimes.next();
				} else {
					nextWaitTime = PERSISTENCE_CREATION_MAX_WAIT_TIME;
				}
				LOG.info("Persistence not reachable. Waiting for {}ms.", nextWaitTime);
				Thread.sleep(nextWaitTime);
			} catch (InterruptedException interrupted) {
				LOG.warn("Thread interrupted while waiting for persistence to be available.", interrupted);
			}
		}
	}

	/**
	 * Connects via REST to the database and retrieves all {@link OrderItem}s and
	 * all {@link Order}s. Then, it triggers the training of the recommender.
	 *
	 * @return The number of elements retrieved from the database or -1 if the
	 *         process failed.
	 */
	public long retrieveDataAndRetrain() {
		setReady(false);
		LOG.trace("Retrieving data objects from database...");

		waitForPersistence();

		List<OrderItem> items = retrieveOrderItems();
		if (items == null) {
			return -1;
		}
		
		List<Order> orders = retrieveOrders();
		if (orders == null) {
			return -1;
		}

		filterLists(items, orders);
		RecommenderSelector.getInstance().train(items, orders);
		LOG.trace("Finished training, ready for recommendation.");
		setReady(true);
		return items.size() + orders.size();
	}

	/**
	 * Retrieves all order items from the persistence service.
	 * 
	 * @return List of OrderItems or null if retrieval failed.
	 */
	private List<OrderItem> retrieveOrderItems() {
		try {
			List<OrderItem> items = LoadBalancedCRUDOperations.getEntities(
					Service.PERSISTENCE, "orderitems", OrderItem.class, -1, -1);
			LOG.trace("Retrieved " + items.size() + " orderItems, starting retrieving of orders now.");
			return items;
		} catch (NotFoundException | LoadBalancerTimeoutException e) {
			handleRetrievalFailure();
			return null;
		}
	}

	/**
	 * Retrieves all orders from the persistence service.
	 * 
	 * @return List of Orders or null if retrieval failed.
	 */
	private List<Order> retrieveOrders() {
		try {
			List<Order> orders = LoadBalancedCRUDOperations.getEntities(
					Service.PERSISTENCE, "orders", Order.class, -1, -1);
			LOG.trace("Retrieved " + orders.size() + " orders, starting training now.");
			return orders;
		} catch (NotFoundException | LoadBalancerTimeoutException e) {
			handleRetrievalFailure();
			return null;
		}
	}

	/**
	 * Handles retrieval failure by setting ready state and logging error.
	 */
	private void handleRetrievalFailure() {
		setReady(true);
		LOG.error("Database retrieving failed.");
	}

	private void filterLists(List<OrderItem> orderItems, List<Order> orders) {
		synchronizeMaxTimeWithPeers();
		if (maxTime == Long.MIN_VALUE) {
			determineMaxTimeFromOrders(orders);
		}
		filterForMaxtimeStamp(orderItems, orders);
	}

	/**
	 * Synchronizes the maximum timestamp with peer recommender services via multicast.
	 */
	private void synchronizeMaxTimeWithPeers() {
		List<Response> maxTimeResponses = ServiceLoadBalancer.multicastRESTOperation(Service.RECOMMENDER,
				"train/timestamp", Response.class,
				client -> client.getService().path(client.getApplicationURI()).path(client.getEndpointURI())
						.request(MediaType.TEXT_PLAIN).accept(MediaType.TEXT_PLAIN).get());
		
		for (Response response : maxTimeResponses) {
			processTimestampResponse(response);
		}
	}

	/**
	 * Processes a single timestamp response from a peer service.
	 * 
	 * @param response The response from a peer recommender service.
	 */
	private void processTimestampResponse(Response response) {
		if (response == null) {
			LOG.warn("One service response was null and is therefore not available for time-check.");
		} else if (response.getStatus() == Response.Status.OK.getStatusCode()) {
			long milliTS = response.readEntity(Long.class);
			updateMaxTimeFromPeer(milliTS);
		} else {
			response.bufferEntity();
			LOG.warn("Service " + response + "was not available for time-check.");
		}
	}

	/**
	 * Updates maxTime based on a peer service's timestamp, using minimum value.
	 * 
	 * @param peerTimestamp The timestamp from a peer service.
	 */
	private void updateMaxTimeFromPeer(long peerTimestamp) {
		if (maxTime != TrainingSynchronizer.DEFAULT_MAX_TIME_VALUE && maxTime != peerTimestamp) {
			LOG.warn("Services disagree about timestamp: " + maxTime + " vs " + peerTimestamp
					+ ". Therfore using the minimum.");
		}
		maxTime = Math.min(maxTime, peerTimestamp);
	}

	/**
	 * Determines the maximum timestamp from the provided orders when no peer data is available.
	 * 
	 * @param orders The list of orders to scan for maximum timestamp.
	 */
	private void determineMaxTimeFromOrders(List<Order> orders) {
		for (Order or : orders) {
			maxTime = Math.max(maxTime, toMillis(or.getTime()));
		}
	}

	private void filterForMaxtimeStamp(List<OrderItem> orderItems, List<Order> orders) {
		removeOrdersAfterMaxTime(orders);
		removeOrphanedOrderItems(orderItems, orders);
	}

	/**
	 * Removes orders that have a timestamp after the maxTime threshold.
	 * 
	 * @param orders The list of orders to filter in place.
	 */
	private void removeOrdersAfterMaxTime(List<Order> orders) {
		List<Order> remove = new ArrayList<>();
		for (Order or : orders) {
			if (toMillis(or.getTime()) > maxTime) {
				remove.add(or);
			}
		}
		orders.removeAll(remove);
	}

	/**
	 * Removes order items whose orders are no longer in the filtered orders list.
	 * 
	 * @param orderItems The list of order items to filter in place.
	 * @param orders The filtered list of valid orders.
	 */
	private void removeOrphanedOrderItems(List<OrderItem> orderItems, List<Order> orders) {
		List<OrderItem> removeItems = new ArrayList<>();
		for (OrderItem orderItem : orderItems) {
			if (!isOrderItemInOrders(orderItem, orders)) {
				removeItems.add(orderItem);
			}
		}
		orderItems.removeAll(removeItems);
	}

	/**
	 * Checks if an order item belongs to any order in the provided list.
	 * 
	 * @param orderItem The order item to check.
	 * @param orders The list of orders to search.
	 * @return True if the order item's order is found, false otherwise.
	 */
	private boolean isOrderItemInOrders(OrderItem orderItem, List<Order> orders) {
		for (Order or : orders) {
			if (or.getId() == orderItem.getOrderId()) {
				return true;
			}
		}
		return false;
	}

	private long toMillis(String date) {
		TemporalAccessor temporalAccessor = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(date);
		LocalDateTime localDateTime = LocalDateTime.from(temporalAccessor);
		ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.systemDefault());
		Instant instant = Instant.from(zonedDateTime);
		return instant.toEpochMilli();
	}

}
