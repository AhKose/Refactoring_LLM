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
		// We have to wait for the database that all entries are created before generating images (which queries
		// persistence). Yes we want to wait forever in case the persistence is not answering.
		Iterator<Integer> waitTimes = PERSISTENCE_CREATION_WAIT_TIME.iterator();
		while (true) {
			if (isPersistenceGenerationFinished()) {
				break;
			}
			sleepBeforeNextPersistenceCheck(waitTimes);
		}
	}

	private boolean isPersistenceGenerationFinished() {
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

	private void sleepBeforeNextPersistenceCheck(Iterator<Integer> waitTimes) {
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

	public long retrieveDataAndRetrain() {
		setReady(false);
		LOG.trace("Retrieving data objects from database...");

		waitForPersistence();

		List<OrderItem> items;
		List<Order> orders;
		try {
			items = retrieveEntities(Service.PERSISTENCE, "orderitems", OrderItem.class);
			long noItems = items.size();
			LOG.trace("Retrieved " + noItems + " orderItems, starting retrieving of orders now.");

			orders = retrieveEntities(Service.PERSISTENCE, "orders", Order.class);
			long noOrders = orders.size();
			LOG.trace("Retrieved " + noOrders + " orders, starting training now.");
		} catch (NotFoundException | LoadBalancerTimeoutException e) {
			// set ready anyway to avoid deadlocks
			setReady(true);
			LOG.error("Database retrieving failed.", e);
			return -1;
		}

		// filter lists
		filterLists(items, orders);
		// train instance
		RecommenderSelector.getInstance().train(items, orders);
		LOG.trace("Finished training, ready for recommendation.");
		setReady(true);
		return items.size() + orders.size();
	}

	private <T> List<T> retrieveEntities(Service service, String endpoint, Class<T> type)
			throws NotFoundException, LoadBalancerTimeoutException {
		return LoadBalancedCRUDOperations.getEntities(service, endpoint, type, -1, -1);
	}

	private void filterLists(List<OrderItem> orderItems, List<Order> orders) {
		// since we are not registered ourselves, we can multicast to all services
		List<Response> maxTimeResponses = ServiceLoadBalancer.multicastRESTOperation(Service.RECOMMENDER,
				"train/timestamp", Response.class,
				client -> client.getService().path(client.getApplicationURI()).path(client.getEndpointURI())
						.request(MediaType.TEXT_PLAIN).accept(MediaType.TEXT_PLAIN).get());

		for (Response response : maxTimeResponses) {
			if (response == null) {
				LOG.warn("One service response was null and is therefore not available for time-check.");
				continue;
			}
			try {
				if (response.getStatus() == Response.Status.OK.getStatusCode()) {
					// only consider if status was fine
					long milliTS = response.readEntity(Long.class);
					updateMaxTimeFromRemote(milliTS);
				} else {
					// release connection by buffering entity
					response.bufferEntity();
					LOG.warn("Service " + response + "was not available for time-check.");
				}
			} finally {
				response.close();
			}
		}

		if (maxTime == Long.MIN_VALUE) {
			// we are the only known service
			// therefore we find max and set it
			for (Order or : orders) {
				maxTime = Math.max(maxTime, toMillis(or.getTime()));
			}
		}
		filterForMaxtimeStamp(orderItems, orders);
	}

	private void updateMaxTimeFromRemote(long milliTS) {
		if (maxTime != TrainingSynchronizer.DEFAULT_MAX_TIME_VALUE && maxTime != milliTS) {
			LOG.warn("Services disagree about timestamp: " + maxTime + " vs " + milliTS + ". Therfore using the minimum.");
		}
		maxTime = Math.min(maxTime, milliTS);
	}

	private void filterForMaxtimeStamp(List<OrderItem> orderItems, List<Order> orders) {
		// filter orderItems and orders and ignore newer entries.
		List<Order> remove = new ArrayList<>();
		for (Order or : orders) {
			if (toMillis(or.getTime()) > maxTime) {
				remove.add(or);
			}
		}
		orders.removeAll(remove);

		Set<Long> validOrderIds = collectOrderIds(orders);
		List<OrderItem> removeItems = new ArrayList<>();
		for (OrderItem orderItem : orderItems) {
			if (!validOrderIds.contains(orderItem.getOrderId())) {
				removeItems.add(orderItem);
			}
		}
		orderItems.removeAll(removeItems);
	}

	private Set<Long> collectOrderIds(List<Order> orders) {
		Set<Long> orderIds = new HashSet<>(orders.size());
		for (Order or : orders) {
			orderIds.add(or.getId());
		}
		return orderIds;
	}


	private long toMillis(String date) {
		TemporalAccessor temporalAccessor = DateTimeFormatter.ISO_LOCAL_DATE_TIME.parse(date);
		LocalDateTime localDateTime = LocalDateTime.from(temporalAccessor);
		ZonedDateTime zonedDateTime = ZonedDateTime.of(localDateTime, ZoneId.systemDefault());
		Instant instant = Instant.from(zonedDateTime);
		return instant.toEpochMilli();
	}

}
