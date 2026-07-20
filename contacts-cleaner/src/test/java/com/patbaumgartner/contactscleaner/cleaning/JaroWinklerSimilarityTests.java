package com.patbaumgartner.contactscleaner.cleaning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class JaroWinklerSimilarityTests {

	@Test
	void identicalStringsScoreOne() {
		assertThat(JaroWinklerSimilarity.of("jane doe", "jane doe")).isEqualTo(1.0);
	}

	@Test
	void completelyDifferentStringsScoreZero() {
		assertThat(JaroWinklerSimilarity.of("abc", "xyz")).isEqualTo(0.0);
	}

	@Test
	void emptyStringScoresZeroAgainstNonEmpty() {
		assertThat(JaroWinklerSimilarity.of("", "jane")).isEqualTo(0.0);
	}

	@ParameterizedTest(name = "{0} vs {1} ≈ {2}")
	@CsvSource(textBlock = """
			'martha',   'marhta',   0.961
			'dwayne',   'duane',    0.840
			'dixon',    'dicksonx', 0.813
			""")
	void matchesReferenceValues(String first, String second, double expected) {
		assertThat(JaroWinklerSimilarity.of(first, second)).isCloseTo(expected, within(0.005));
	}

	@Test
	void commonPrefixBoostsSimilarity() {
		double withPrefix = JaroWinklerSimilarity.of("jane doe", "jane doa");
		double withoutPrefix = JaroWinklerSimilarity.of("ejan doe", "ajan doe");
		assertThat(withPrefix).isGreaterThan(withoutPrefix);
	}

}
