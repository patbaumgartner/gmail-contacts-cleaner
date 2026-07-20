package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicatePhoneNumberRemovalRuleTests {

	private final DuplicatePhoneNumberRemovalRule rule = new DuplicatePhoneNumberRemovalRule();

	@Test
	void removesExactDuplicatesKeepingFirstOccurrence() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41446681800"));
		vcard.addTelephoneNumber(new Telephone("+41446681801"));
		vcard.addTelephoneNumber(new Telephone("+41446681800"));

		assertThat(rule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers()).extracting(Telephone::getText)
			.containsExactly("+41446681800", "+41446681801");
	}

	@Test
	void leavesUniqueNumbersUntouched() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41446681800"));
		vcard.addTelephoneNumber(new Telephone("+41446681801"));

		assertThat(rule.apply(vcard)).isFalse();
		assertThat(vcard.getTelephoneNumbers()).hasSize(2);
	}

	@Test
	void handlesContactWithoutPhoneNumbers() {
		assertThat(rule.apply(new VCard())).isFalse();
	}

}
