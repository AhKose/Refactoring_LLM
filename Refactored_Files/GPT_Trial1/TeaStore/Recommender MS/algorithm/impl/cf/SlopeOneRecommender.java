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
		validateUserId(userid);
		validateUserHasHistory(userid);

		Map<Long, Double> importances = getUserVector(userid);
		return filterRecommendations(importances, currentItems);

	}

	private void validateUserId(Long userid) {
		if (userid == null) {
			throw new UseFallBackException(this.getClass().getName()
					+ " does not support null userids. Use a pseudouser or switch to another approach.");
		}
	}

	private void validateUserHasHistory(Long userid) {
		if (getUserBuyingMatrix().get(userid) == null) {
			// this user has not bought anything yet, so we do not have any information
			throw new UseFallBackException("No user information.");
		}
	}

	protected Map<Long, Double> getUserVector(Long userid) {
		HashMap<Long, Double> importances = new HashMap<>();
		for (Long productid : getTotalProducts()) {
			importances.put(productid, safeCalculateScore(userid, productid));
		}
		return importances;
	}

	private double safeCalculateScore(Long userid, Long productid) {
		try {
			return calculateScoreForItem(userid.longValue(), productid.longValue());
		} catch (NullPointerException e) {
			// this exception can be thrown if we have not enough information
			return -1.0;
		}
	}

	private double calculateScoreForItem(long userid, long itemid) {
		double score = 0;
		double cumWeights = 0;

		for (Entry<Long, Double> useritem : getUserBuyingMatrix().get(userid).entrySet()) {
			if (isSameItem(useritem.getKey(), itemid)) {
				return useritem.getValue();
			}

			int frequency = requireFrequency(useritem.getKey(), itemid);
			score = accumulateScore(score, useritem.getValue(), useritem.getKey(), itemid, frequency);
			cumWeights += frequency;
		}
		return score / cumWeights;
	}

	private boolean isSameItem(Long knownItemId, long itemid) {
		return knownItemId != null && knownItemId.longValue() == itemid;
	}

	private int requireFrequency(Long knownItemId, long itemid) {
		Integer frequency = frequencies.get(knownItemId).get(itemid);
		if (frequency == null) {
			throw new NullPointerException();
		}
		return frequency.intValue();
	}

	private double accumulateScore(double currentScore, double userValue, Long knownItemId, long itemid, int frequency) {
		currentScore += userValue * frequency;
		currentScore += requireDifference(knownItemId, itemid) * frequency;
		return currentScore;
	}

	private double requireDifference(Long knownItemId, long itemid) {
		Double diff = differences.get(knownItemId).get(itemid);
		if (diff == null) {
			throw new NullPointerException();
		}
		return diff.doubleValue();
	}

	@Override
	protected void executePreprocessing() {
		resetMatrices();
		buildDifferencesMatrices(getUserBuyingMatrix());
	}

	private void resetMatrices() {
		if (differences == null) {
			differences = new HashMap<>();
		} else {
			differences.clear();
		}

		if (frequencies == null) {
			frequencies = new HashMap<>();
		} else {
			frequencies.clear();
		}
	}

	private void buildDifferencesMatrices(Map<Long, Map<Long, Double>> userRatingMatrix) {
		for (Map<Long, Double> uservalues : userRatingMatrix.values()) {
			updateMatricesForUser(uservalues);
		}
		normalizeDifferences();
	}

	private void updateMatricesForUser(Map<Long, Double> uservalues) {
		for (Entry<Long, Double> singleRating : uservalues.entrySet()) {
			Long itemId = singleRating.getKey();
			Map<Long, Integer> freqRow = getOrCreateFrequencyRow(itemId);
			Map<Long, Double> diffRow = getOrCreateDifferenceRow(itemId);

			for (Entry<Long, Double> otherRating : uservalues.entrySet()) {
				Long otherItemId = otherRating.getKey();

				int currCount = getOrDefault(freqRow, otherItemId, 0);
				double currDiff = getOrDefault(diffRow, otherItemId, 0.0);

				double userdiff = singleRating.getValue() - otherRating.getValue();
				freqRow.put(otherItemId, currCount + 1);
				diffRow.put(otherItemId, currDiff + userdiff);
			}
		}
	}

	private Map<Long, Integer> getOrCreateFrequencyRow(Long itemId) {
		Map<Long, Integer> row = frequencies.get(itemId);
		if (row == null) {
			row = new HashMap<>();
			frequencies.put(itemId, row);
		}
		return row;
	}

	private Map<Long, Double> getOrCreateDifferenceRow(Long itemId) {
		Map<Long, Double> row = differences.get(itemId);
		if (row == null) {
			row = new HashMap<>();
			differences.put(itemId, row);
		}
		return row;
	}

	private int getOrDefault(Map<Long, Integer> map, Long key, int defaultValue) {
		Integer value = map.get(key);
		return value == null ? defaultValue : value.intValue();
	}

	private double getOrDefault(Map<Long, Double> map, Long key, double defaultValue) {
		Double value = map.get(key);
		return value == null ? defaultValue : value.doubleValue();
	}

	private void normalizeDifferences() {
		for (Long i : differences.keySet()) {
			Map<Long, Double> diffRow = differences.get(i);
			Map<Long, Integer> freqRow = frequencies.get(i);

			for (Long j : diffRow.keySet()) {
				double diffval = diffRow.get(j);
				double freq = freqRow.get(j);
				diffRow.put(j, diffval / freq);
			}
		}
	}
}
