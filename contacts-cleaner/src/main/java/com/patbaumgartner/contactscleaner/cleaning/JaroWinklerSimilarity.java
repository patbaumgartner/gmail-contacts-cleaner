package com.patbaumgartner.contactscleaner.cleaning;

/**
 * Jaro-Winkler string similarity — a well-established metric for short strings such as
 * person names. Implemented locally to avoid a dependency for ~60 lines of math.
 *
 * <p>
 * Returns a value between {@code 0.0} (completely different) and {@code 1.0} (equal),
 * boosting strings that share a common prefix, which suits name matching well
 * ({@code "Jane Doe"} vs {@code "Jane Doe-Muster"}).
 */
final class JaroWinklerSimilarity {

	private static final double PREFIX_SCALING_FACTOR = 0.1;

	private static final int MAX_PREFIX_LENGTH = 4;

	private JaroWinklerSimilarity() {
	}

	/**
	 * Computes the Jaro-Winkler similarity of two strings.
	 * @param first the first string
	 * @param second the second string
	 * @return similarity in {@code [0.0, 1.0]}
	 */
	static double of(String first, String second) {
		double jaro = jaro(first, second);
		int prefix = commonPrefixLength(first, second);
		return jaro + prefix * PREFIX_SCALING_FACTOR * (1 - jaro);
	}

	private static double jaro(String first, String second) {
		if (first.equals(second)) {
			return 1.0;
		}
		if (first.isEmpty() || second.isEmpty()) {
			return 0.0;
		}
		int matchWindow = Math.max(first.length(), second.length()) / 2 - 1;
		boolean[] firstMatched = new boolean[first.length()];
		boolean[] secondMatched = new boolean[second.length()];

		int matches = 0;
		for (int i = 0; i < first.length(); i++) {
			int from = Math.max(0, i - matchWindow);
			int to = Math.min(second.length(), i + matchWindow + 1);
			for (int j = from; j < to; j++) {
				if (!secondMatched[j] && first.charAt(i) == second.charAt(j)) {
					firstMatched[i] = true;
					secondMatched[j] = true;
					matches++;
					break;
				}
			}
		}
		if (matches == 0) {
			return 0.0;
		}

		int transpositions = 0;
		int k = 0;
		for (int i = 0; i < first.length(); i++) {
			if (firstMatched[i]) {
				while (!secondMatched[k]) {
					k++;
				}
				if (first.charAt(i) != second.charAt(k)) {
					transpositions++;
				}
				k++;
			}
		}

		double m = matches;
		return (m / first.length() + m / second.length() + (m - transpositions / 2.0) / m) / 3.0;
	}

	private static int commonPrefixLength(String first, String second) {
		int max = Math.min(MAX_PREFIX_LENGTH, Math.min(first.length(), second.length()));
		int length = 0;
		while (length < max && first.charAt(length) == second.charAt(length)) {
			length++;
		}
		return length;
	}

}
