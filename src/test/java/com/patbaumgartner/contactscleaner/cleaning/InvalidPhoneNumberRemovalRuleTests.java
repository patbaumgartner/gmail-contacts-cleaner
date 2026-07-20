package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidPhoneNumberRemovalRuleTests {

	private final InvalidPhoneNumberRemovalRule swissRule = new InvalidPhoneNumberRemovalRule("CH");

	private final InvalidPhoneNumberRemovalRule regionlessRule = new InvalidPhoneNumberRemovalRule("");

	@ParameterizedTest
	@ValueSource(strings = { "*133#", "12 9001", "101 8005", "1818", "MAILBOX", "+4144", "0446681" })
	void removesUndialableFragmentsWithRegion(String invalid) {
		assertThat(this.swissRule.isProvablyInvalid(invalid)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "+41446681800", "+41791234567", "0446681800", "+16503805205", "+493090182000" })
	void keepsValidNumbers(String valid) {
		assertThat(this.swissRule.isProvablyInvalid(valid)).isFalse();
	}

	@Test
	void withoutRegionNationalNumbersAreNotJudged() {
		// "0446681800" is Swiss-valid but cannot be judged without a region — keep.
		assertThat(this.regionlessRule.isProvablyInvalid("0446681800")).isFalse();
		// Fragments that look national are also kept without a region.
		assertThat(this.regionlessRule.isProvablyInvalid("12 9001")).isFalse();
		// International numbers are always judgeable.
		assertThat(this.regionlessRule.isProvablyInvalid("+41446681800")).isFalse();
		assertThat(this.regionlessRule.isProvablyInvalid("+4144")).isTrue();
	}

	@Test
	void removesOnlyInvalidNumbersFromContact() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41446681800"));
		vcard.addTelephoneNumber(new Telephone("12 9001"));

		assertThat(this.swissRule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41446681800");
	}

	@Test
	void reportsNoChangeForCleanContacts() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41446681800"));

		assertThat(this.swissRule.apply(vcard)).isFalse();
	}

}
