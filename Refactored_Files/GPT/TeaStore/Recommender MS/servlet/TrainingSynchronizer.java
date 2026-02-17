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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;


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
		// case the persistence is not answering.
		Iterator<Integer> waitTimes = PERSISTENCE_CREATION_WAIT_TIME.iterator();
		while (!isPersistenceFinished()) {
			sleepBeforeRetry(waitTimes);
		}
	}

	private boolean isPersistenceFinished() {
		Response result = null;
		try {
			result = ServiceLoadBalancer.loadBalanceRESTOperation(Service.PERSISTENCE, "generatedb", String.class,
					client -> client.getService().path(client.getApplicationURI()).path(client.getEndpointURI())
							.path("finished").request().get());
			return result != null && Boolean.parseBoolean(result.readEntity(String.class));
		} catch (NullPointerException | NotFoundException | LoadBalancerTimeoutException e) {
			// continue waiting as usual
			return false;
		} finally {
			if (result != null) {
				result.close();
			}
		}
	}

	private void sleepBeforeRetry(Iterator<Integer> waitTimes) {
		int nextWaitTime = waitTimes.hasNext() ? waitTimes.next() : PERSISTENCE_CREATION_MAX_WAIT_TIME;
		try {
			LOG.info("Persistence not reachable. Waiting for {}ms.", nextWaitTime);
			Thread.sleep(nextWaitTime);
		} catch (InterruptedException interrupted) {
			LOG.warn("Thread interrupted while waiting for persistence to be available.", interrupted);
		}
	}

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

	private List<OrderItem> retrieveOrderItems() {
		try {
			List<OrderItem> items = LoadBalancedCRUDOperations.getEntities(Service.PERSISTENCE, "orderitems",
					OrderItem.class, -1, -1);
			LOG.trace("Retrieved " + items.size() + " orderItems, starting retrieving of orders now.");
			return items;
		} catch (NotFoundException | LoadBalancerTimeoutException e) {
			return failRetrieval();
		}
	}

	private List<Order> retrieveOrders() {
		try {
			List<Order> orders = LoadBalancedCRUDOperations.getEntities(Service.PERSISTENCE, "orders", Order.class, -1,
					-1);
			LOG.trace("Retrieved " + orders.size() + " orders, starting training now.");
			return orders;
		} catch (NotFoundException | LoadBalancerTimeoutException e) {
			return failRetrieval();
		}
	}

	private <T> T failRetrieval() {
		// set ready anyway to avoid deadlocks
		setReady(true);
		LOG.error("Database retrieving failed.");
		return null;
	}

	private void filterLists(List<OrderItem> orderItems, List<Order> orders) {
		long clusterMinTimestamp = queryClusterMinTimestamp();
		if (clusterMinTimestamp != DEFAULT_MAX_TIME_VALUE) {
			applyClusterTimestamp(clusterMinTimestamp);
		}

		if (maxTime == DEFAULT_MAX_TIME_VALUE) {
			// either we are the only known service or nobody exposed a timestamp yet
			maxTime = findMaxOrderTimestamp(orders);
		}

		filterForMaxtimeStamp(orderItems, orders);
	}

	private long queryClusterMinTimestamp() {
		List<Response> maxTimeResponses = ServiceLoadBalancer.multicastRESTOperation(Service.RECOMMENDER, "train/timestamp",
				Response.class, client -> client.getService().path(client.getApplicationURI()).path(client.getEndpointURI())
						.request(MediaType.TEXT_PLAIN).accept(MediaType.TEXT_PLAIN).get());

		long minTimestamp = Long.MAX_VALUE;
		boolean foundOkResponse = false;

		for (Response response : maxTimeResponses) {
			if (response == null) {
				LOG.warn("One service response was null and is therefore not available for time-check.");
				continue;
			}

			try {
				if (response.getStatus() == Response.Status.OK.getStatusCode()) {
					long milliTS = response.readEntity(Long.class);
					minTimestamp = Math.min(minTimestamp, milliTS);
					foundOkResponse = true;
				} else {
					response.bufferEntity();
					LOG.warn("Service " + response + "was not available for time-check.");
				}
			} finally {
				response.close();
			}
		}

		return foundOkResponse ? minTimestamp : DEFAULT_MAX_TIME_VALUE;
	}

	private void applyClusterTimestamp(long clusterMinTimestamp) {
		if (maxTime != DEFAULT_MAX_TIME_VALUE && maxTime != clusterMinTimestamp) {
			LOG.warn("Services disagree about timestamp: " + maxTime + " vs " + clusterMinTimestamp
					+ ". Therfore using the minimum.");
		}

		if (maxTime == DEFAULT_MAX_TIME_VALUE) {
			maxTime = clusterMinTimestamp;
		} else {
			maxTime = Math.min(maxTime, clusterMinTimestamp);
		}
	}

	private long findMaxOrderTimestamp(List<Order> orders) {
		long max = DEFAULT_MAX_TIME_VALUE;
		for (Order or : orders) {
			max = Math.max(max, toMillis(or.getTime()));
		}
		return max;
	}

	private void filterForMaxtimeStamp(List<OrderItem> orderItems, List<Order> orders) {
		removeOrdersAfterMaxTime(orders);
		removeOrderItemsWithoutOrder(orderItems, orders);
	}

	private void removeOrdersAfterMaxTime(List<Order> orders) {
		List<Order> remove = new ArrayList<>();
		for (Order or : orders) {
			if (toMillis(or.getTime()) > maxTime) {
				remove.add(or);
			}
		}
		orders.removeAll(remove);
	}

	private void removeOrderItemsWithoutOrder(List<OrderItem> orderItems, List<Order> orders) {
		Set<Long> validOrderIds = new HashSet<>();
		for (Order or : orders) {
			validOrderIds.add(or.getId());
		}

		List<OrderItem> removeItems = new ArrayList<>();
		for (OrderItem orderItem : orderItems) {
			if (!validOrderIds.contains(orderItem.getOrderId())) {
				removeItems.add(orderItem);
			}
		}
		orderItems.removeAll(removeItems);
	}

	private long toMillis(String date) {
		TemporalAccessor temporalAccessor = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(date);
		LocalDateTime localDateTime = LocalDateTime.from(temporalAccessor);
		ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.systemDefault());
		Instant instant = Instant.from(zonedDateTime);
		return instant.toEpochMilli();
	}

}
