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
		// Entered wrongly: given=Girba family=Tudor, but the e-mail says tudor.girba
		VCard vcard = contact("Girba", "Tudor", "tudor.girba@example.com");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Tudor");
		assertThat(vcard.getStructuredName().getFamily()).isEqualTo("Girba");
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Tudor Girba");
	}

	@Test
	void supportsDashUnderscoreAndConcatenatedLocalParts() {
		assertThat(this.rule.apply(contact("Doe", "Jane", "jane-doe@example.com"))).isTrue();
		assertThat(this.rule.apply(contact("Doe", "Jane", "jane_doe@example.com"))).isTrue();
		assertThat(this.rule.apply(contact("Doe", "Jane", "janedoe@example.com"))).isTrue();
	}

	@Test
	void foldsAccentsWhenMatching() {
		VCard vcard = contact("Bolboacă", "Adrian", "adrian.bolboaca@example.com");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Adrian");
	}

	@Test
	void neverTouchesCorrectlyOrderedNames() {
		VCard vcard = contact("Jane", "Doe", "jane.doe@example.com");

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Jane");
	}

	@Test
	void neverGuessesWithoutEmailEvidence() {
		assertThat(this.rule.apply(contact("Girba", "Tudor"))).isFalse();
		assertThat(this.rule.apply(contact("Girba", "Tudor", "info@example.com"))).isFalse();
	}

	@Test
	void ambiguousEvidenceIsIgnored() {
		// One address per order — cannot decide.
		VCard vcard = contact("Girba", "Tudor", "tudor.girba@a.example", "girba.tudor@b.example");

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
