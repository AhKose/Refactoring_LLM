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
package tools.descartes.teastore.recommender.algorithm.impl.cf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import tools.descartes.teastore.recommender.algorithm.AbstractRecommender;
import tools.descartes.teastore.recommender.algorithm.impl.UseFallBackException;

/**
 * Recommender based on item-based collaborative filtering with the slope one
 * algorithm.
 * 
 * @author Johannes Grohmann
 *
 */
public class SlopeOneRecommender extends AbstractRecommender {

	/**
	 * Represents a matrix, assigning each itemid an average difference (in
	 * rating/buying) to any other itemid.
	 */
	private Map<Long, Map<Long, Double>> differences = new HashMap<>();

	/**
	 * Represents a matrix, counting the frequencies of each combination (i.e. users
	 * rating/buying both items).
	 */
	private Map<Long, Map<Long, Integer>> frequencies = new HashMap<>();

	/**
	 * @return the differences
	 */
	public Map<Long, Map<Long, Double>> getDifferences() {
		return differences;
	}

	/**
	 * @param differences
	 *            the differences to set
	 */
	public void setDifferences(Map<Long, Map<Long, Double>> differences) {
		this.differences = differences;
	}

	/**
	 * @return the frequencies
	 */
	public Map<Long, Map<Long, Integer>> getFrequencies() {
		return frequencies;
	}

	/**
	 * @param frequencies
	 *            the frequencies to set
	 */
	public void setFrequencies(Map<Long, Map<Long, Integer>> frequencies) {
		this.frequencies = frequencies;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * tools.descartes.teastore.recommender.algorithm.AbstractRecommender#
	 * execute(java.util.List)
	 */
	@Override
	protected List<Long> execute(Long userid, List<Long> currentItems) {
		if (userid == null) {
			throw new UseFallBackException(this.getClass().getName()
					+ " does not support null userids. Use a pseudouser or switch to another approach.");
		}
		if (getUserBuyingMatrix().get(userid) == null) {
			// this user has not bought anything yet, so we do not have any information
			throw new UseFallBackException("No user information.");
		}
		Map<Long, Double> importances = getUserVector(userid);
		return filterRecommendations(importances, currentItems);

	}

	/**
	 * Generates one row of the matrix for the given user. (Predicts the user score
	 * for each product ID.)
	 * 
	 * @param userid
	 *            The user to predict for
	 * @return A Map assigning each product ID a (predicted) score (for the given
	 *         user)
	 */
	protected Map<Long, Double> getUserVector(Long userid) {
		// This could be further optimized by moving this part into the pre-processing
		// step, but we want to have nicer performance behavior
		HashMap<Long, Double> importances = new HashMap<>();
		for (Long productid : getTotalProducts()) {
			try {
				importances.put(productid, calculateScoreForItem(userid, productid));
			} catch (NullPointerException e) {
				// this exception can be thrown if we have not enough information
				importances.put(productid, -1.0);
			}
		}
		return importances;
	}

	private double calculateScoreForItem(long userid, long itemid) {
		double score = 0;
		double cumWeights = 0;
		for (Entry<Long, Double> useritem : getUserBuyingMatrix().get(userid).entrySet()) {
			// if we find that the user actually bought this item before, we can return this
			// value
			// (considering it is his rating, we can directly return this rating)
			if (useritem.getKey() == itemid) {
				return useritem.getValue();
			}
			// if not, we can calculate the (expected) rating for that user based on item i
			int frequency = frequencies.get(useritem.getKey()).get(itemid);
			score += useritem.getValue() * frequency;
			score += differences.get(useritem.getKey()).get(itemid) * frequency;
			cumWeights += frequency;
		}
		// normalize
		return score / cumWeights;
	}

	@Override
	protected void executePreprocessing() {
		// The buying matrix is considered to be the rating
		// i.e. the more buys, the higher the rating
		buildDifferencesMatrices(getUserBuyingMatrix());
	}

	/**
	 * Based on the available data, calculate the relationships between the items
	 * and number of occurrences. Fill the difference and frequencies matrix.
	 * 
	 * @param userRatingMatrix
	 *            The user rating matrix
	 */
	private void buildDifferencesMatrices(Map<Long, Map<Long, Double>> userRatingMatrix) {
		accumulateRatingDifferences(userRatingMatrix);
		normalizeDifferencesMatrix();
	}

	/**
	 * Accumulates rating differences and frequencies across all users.
	 * 
	 * @param userRatingMatrix The user rating matrix to process.
	 */
	private void accumulateRatingDifferences(Map<Long, Map<Long, Double>> userRatingMatrix) {
		for (Map<Long, Double> uservalues : userRatingMatrix.values()) {
			for (Entry<Long, Double> singleRating : uservalues.entrySet()) {
				initializeMatrixEntriesIfAbsent(singleRating.getKey());
				updateMatrixEntriesForUser(uservalues, singleRating);
			}
		}
	}

	/**
	 * Initializes matrix entries for a product ID if not already present.
	 * 
	 * @param productId The product ID to initialize entries for.
	 */
	private void initializeMatrixEntriesIfAbsent(Long productId) {
		if (!frequencies.containsKey(productId)) {
			frequencies.put(productId, new HashMap<Long, Integer>());
			differences.put(productId, new HashMap<Long, Double>());
		}
	}

	/**
	 * Updates frequency and difference matrices for all item pairs involving the given rating.
	 * 
	 * @param uservalues All ratings from the current user.
	 * @param singleRating The specific rating entry being processed.
	 */
	private void updateMatrixEntriesForUser(Map<Long, Double> uservalues, Entry<Long, Double> singleRating) {
		for (Entry<Long, Double> otherRating : uservalues.entrySet()) {
			int currCount = getCurrentFrequency(singleRating.getKey(), otherRating.getKey());
			double currDiff = getCurrentDifference(singleRating.getKey(), otherRating.getKey());
			double userdiff = singleRating.getValue() - otherRating.getValue();
			
			frequencies.get(singleRating.getKey()).put(otherRating.getKey(), currCount + 1);
			differences.get(singleRating.getKey()).put(otherRating.getKey(), currDiff + userdiff);
		}
	}

	/**
	 * Retrieves the current frequency count for an item pair, defaulting to 0.
	 * 
	 * @param itemI First item ID.
	 * @param itemJ Second item ID.
	 * @return The current frequency count or 0 if not present.
	 */
	private int getCurrentFrequency(Long itemI, Long itemJ) {
		Integer count = frequencies.get(itemI).get(itemJ);
		return (count != null) ? count.intValue() : 0;
	}

	/**
	 * Retrieves the current accumulated difference for an item pair, defaulting to 0.0.
	 * 
	 * @param itemI First item ID.
	 * @param itemJ Second item ID.
	 * @return The current difference or 0.0 if not present.
	 */
	private double getCurrentDifference(Long itemI, Long itemJ) {
		Double diff = differences.get(itemI).get(itemJ);
		return (diff != null) ? diff.doubleValue() : 0.0;
	}

	/**
	 * Normalizes the differences matrix by dividing each entry by its frequency.
	 */
	private void normalizeDifferencesMatrix() {
		for (Long i : differences.keySet()) {
			for (Long j : differences.get(i).keySet()) {
				double diffval = differences.get(i).get(j);
				double freq = frequencies.get(i).get(j);
				differences.get(i).put(j, diffval / freq);
			}
		}
	}
}
