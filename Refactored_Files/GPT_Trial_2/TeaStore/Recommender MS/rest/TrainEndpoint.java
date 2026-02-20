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
package tools.descartes.teastore.recommender.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import tools.descartes.teastore.recommender.algorithm.IRecommender;
import tools.descartes.teastore.recommender.servlet.TrainingSynchronizer;
import tools.descartes.teastore.entities.Order;
import tools.descartes.teastore.entities.OrderItem;

/**
 * REST endpoint to trigger the (re)training of the Recommender.
 *
 * @author Johannes Grohmann
 *
 */
@Path("train")
@Produces({ "text/plain", "application/json" })
public class TrainEndpoint {

	/**
	 * Triggers the training of the recommendation algorithm. It retrieves all data
	 * {@link OrderItem}s and all {@link Order}s from the database entity and is
	 * therefore both very network and computation time intensive. <br>
	 * This method must be called before the {@link RecommendEndpoint} is usable, as
	 * the {@link IRecommender} will throw an
	 * {@link UnsupportedOperationException}.<br>
	 * Calling this method a second time initiates a new training process from scratch.
	 *
	 * @return Returns a {@link Response} with
	 *         {@link jakarta.servlet.http.HttpServletResponse#SC_OK} or with
	 *         {@link jakarta.servlet.http.HttpServletResponse#SC_INTERNAL_SERVER_ERROR},
	 *         if the operation failed.
	 */
	private static final Logger LOG = LoggerFactory.getLogger(TrainEndpoint.class);

	@GET
	public Response train() {
		long start = System.currentTimeMillis();
		try {
			long number = TrainingSynchronizer.getInstance().retrieveDataAndRetrain();
			long time = System.currentTimeMillis() - start;
			if (number != -1) {
				return buildSuccessResponse(time, number);
			}
		} catch (Exception e) {
			LOG.error("The (re)trainprocess failed.", e);
		}
		return buildFailureResponse();
	}

	private Response buildSuccessResponse(long timeMs, long number) {
		return Response.ok("The (re)train was succesfully done. It took " + timeMs + "ms and " + number
				+ " of Orderitems were retrieved from the database.").build();
	}

	private Response buildFailureResponse() {
		// set ready to true anyway to avoid being stuck
		return Response.status(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode())
				.entity("The (re)trainprocess failed.").build();
	}

	/**
	 * Returns the last time stamp, which was considered at the training of this
	 * instance.
	 *
	 * @return Returns a {@link Response} with
	 *         {@link jakarta.servlet.http.HttpServletResponse#SC_OK} containing the
	 *         maximum considered time as String or with
	 *         {@link jakarta.servlet.http.HttpServletResponse#SC_INTERNAL_SERVER_ERROR},
	 *         if the operation failed.
	 */
	@GET
	@Path("timestamp")
	public Response getTimeStamp() {
		if (TrainingSynchronizer.getInstance().getMaxTime() == TrainingSynchronizer.DEFAULT_MAX_TIME_VALUE) {
			return Response.status(Response.Status.PRECONDITION_FAILED.getStatusCode())
					.entity("The collection of the current maxTime was not possible.").build();
		}
		return Response.ok(TrainingSynchronizer.getInstance().getMaxTime()).build();
	}

	/**
	 * This methods checks, if the service is ready to serve recommendation
	 * requests, i.e., if the algorithm has finished training and no retraining process
	 * is running. However, this does not imply that issuing a recommendation will
	 * fail, if this method returns false. For example, if a retraining is issued,
	 * the old trained instance might still answer issued requests until the new
	 * instance is fully trained. However, performance behavior is probably
	 * influenced.
	 *
	 * @return True, if recommender is ready; false, if not.
	 */
	@GET
	@Path("isready")
	public Response isReady() {
		boolean ready = TrainingSynchronizer.getInstance().isReady();
		return ready ? Response.ok(true).build() : Response.serverError().entity(false).build();
	}
}
