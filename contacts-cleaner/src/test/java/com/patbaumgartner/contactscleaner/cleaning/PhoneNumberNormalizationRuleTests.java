package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNumberNormalizationRuleTests {

	private final PhoneNumberNormalizationRule rule = new PhoneNumberNormalizationRule("");

	@ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
	@CsvSource(textBlock = """
			'044 668 18 00',           '0446681800'
			'044-668-18-00',           '0446681800'
			'044.668.18.00',           '0446681800'
			'(044) 668 18 00',         '0446681800'
			'0041 44 668 18 00',       '+41446681800'
			'0041-44-668-18-00',       '+41446681800'
			'+41 44 668 18 00',        '+41446681800'
			'  0446681800  ',          '0446681800'
			'+41446681800',            '+41446681800'
			""")
	void normalizesPhoneNumbers(String input, String expected) {
		assertThat(rule.normalize(input)).isEqualTo(expected);
	}

	@Test
	void returnsNullForNullInput() {
		assertThat(rule.normalize(null)).isNull();
	}

	@Test
	void reportsChangeOnlyWhenNumberWasModified() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41446681800"));
		assertThat(rule.apply(vcard)).isFalse();

		vcard.addTelephoneNumber(new Telephone("0041 44 668 18 01"));
		assertThat(rule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers()).extracting(Telephone::getText)
			.containsExactly("+41446681800", "+41446681801");
	}

}
