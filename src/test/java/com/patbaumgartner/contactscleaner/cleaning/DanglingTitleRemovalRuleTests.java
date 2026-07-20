package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

import ezvcard.VCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DanglingTitleRemovalRuleTests {

	private final DanglingTitleRemovalRule rule = new DanglingTitleRemovalRule();

	@Test
	void removesTitlesWithoutAnyOrganization() {
		VCard vcard = new VCard();
		vcard.addTitle("Senior Consultant");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getTitles()).isEmpty();
	}

	@Test
	void keepsTitlesBackedByAnOrganization() {
		VCard vcard = new VCard();
		vcard.setOrganization("ACME Corp");
		vcard.addTitle("Senior Consultant");

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getTitles()).hasSize(1);
	}

	@Test
	void titleOrphanedByOrganizationRemovalIsCaughtInTheSameRun() {
		VCard vcard = new VCard();
		vcard.setOrganization("Namics AG");
		vcard.addTitle("Principal Consultant");

		// Simulate the production rule order: org removal first, then dangling titles.
		new OrganizationRemovalRule(List.of("Namics")).apply(vcard);
		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getTitles()).isEmpty();
	}

	@Test
	void handlesContactsWithoutTitles() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
