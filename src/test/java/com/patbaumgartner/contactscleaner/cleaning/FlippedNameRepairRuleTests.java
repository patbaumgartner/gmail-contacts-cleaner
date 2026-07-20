package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FlippedNameRepairRuleTests {

	private final FlippedNameRepairRule rule = new FlippedNameRepairRule();

	private static VCard contact(String given, String family, String... emails) {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven(given);
		name.setFamily(family);
		vcard.setStructuredName(name);
		vcard.setFormattedName(new FormattedName((given + " " + family).trim()));
		for (String email : emails) {
			vcard.addEmail(new Email(email));
		}
		return vcard;
	}

	@Test
	void flipsNamesWhenTheEmailProvesTheOrder() {
		// Entered wrongly: given=Muster family=Max, but the e-mail says max.muster
		VCard vcard = contact("Muster", "Max", "max.muster@example.com");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Max");
		assertThat(vcard.getStructuredName().getFamily()).isEqualTo("Muster");
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Max Muster");
	}

	@Test
	void supportsDashUnderscoreAndConcatenatedLocalParts() {
		assertThat(this.rule.apply(contact("Doe", "Jane", "jane-doe@example.com"))).isTrue();
		assertThat(this.rule.apply(contact("Doe", "Jane", "jane_doe@example.com"))).isTrue();
		assertThat(this.rule.apply(contact("Doe", "Jane", "janedoe@example.com"))).isTrue();
	}

	@Test
	void foldsAccentsWhenMatching() {
		VCard vcard = contact("García", "José", "jose.garcia@example.com");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("José");
	}

	@Test
	void neverTouchesCorrectlyOrderedNames() {
		VCard vcard = contact("Jane", "Doe", "jane.doe@example.com");

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Jane");
	}

	@Test
	void neverGuessesWithoutEmailEvidence() {
		assertThat(this.rule.apply(contact("Muster", "Max"))).isFalse();
		assertThat(this.rule.apply(contact("Muster", "Max", "info@example.com"))).isFalse();
	}

	@Test
	void ambiguousEvidenceIsIgnored() {
		// One address per order — cannot decide.
		VCard vcard = contact("Muster", "Max", "max.muster@a.example", "muster.max@b.example");

		assertThat(this.rule.apply(vcard)).isFalse();
	}

	@Test
	void skipsContactsWithoutBothNameParts() {
		assertThat(this.rule.apply(contact("Jane", "", "jane@example.com"))).isFalse();
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

	@Test
	void identicalGivenAndFamilyAreLeftAlone() {
		assertThat(this.rule.apply(contact("James", "James", "james.james@example.com"))).isFalse();
	}

}
