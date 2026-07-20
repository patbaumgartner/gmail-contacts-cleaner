package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.FormattedName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SelfOrganizationRemovalRuleTests {

	private final SelfOrganizationRemovalRule rule = new SelfOrganizationRemovalRule();

	@Test
	void removesOrganizationsRepeatingThePersonsName() {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName("Jane Doe"));
		vcard.setOrganization("Jane Doe");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getOrganizations()).isEmpty();
	}

	@Test
	void matchingIgnoresCaseAndWhitespace() {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName("Jane  Doe"));
		vcard.setOrganization("jane doe");

		assertThat(this.rule.apply(vcard)).isTrue();
	}

	@Test
	void keepsRealOrganizations() {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName("Jane Doe"));
		vcard.setOrganization("Jane Doe Consulting GmbH");

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getOrganizations()).hasSize(1);
	}

	@Test
	void handlesContactsWithoutNameOrOrganization() {
		VCard nameless = new VCard();
		nameless.setOrganization("ACME Corp");
		assertThat(this.rule.apply(nameless)).isFalse();

		VCard orgless = new VCard();
		orgless.setFormattedName(new FormattedName("Jane Doe"));
		assertThat(this.rule.apply(orgless)).isFalse();
	}

}
