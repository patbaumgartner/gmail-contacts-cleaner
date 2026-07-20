package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

import ezvcard.VCard;
import ezvcard.property.Organization;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdditionalOrganizationsRemovalRuleTests {

	private final AdditionalOrganizationsRemovalRule rule = new AdditionalOrganizationsRemovalRule();

	private static Organization orgOf(String name) {
		Organization organization = new Organization();
		organization.getValues().add(name);
		return organization;
	}

	@Test
	void keepsOnlyThePrimaryOrganization() {
		VCard vcard = new VCard();
		vcard.addOrganization(orgOf("Current Employer AG"));
		vcard.addOrganization(orgOf("Previous Job GmbH"));
		vcard.addOrganization(orgOf("Even Older Inc"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getOrganizations()).hasSize(1);
		assertThat(vcard.getOrganizations().getFirst().getValues()).containsExactly("Current Employer AG");
	}

	@Test
	void singleOrganizationIsUntouched() {
		VCard vcard = new VCard();
		vcard.addOrganization(orgOf("Current Employer AG"));

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getOrganizations()).hasSize(1);
	}

	@Test
	void firstMeansFirstSurvivorOfTheDefunctOrgRule() {
		VCard vcard = new VCard();
		vcard.addOrganization(orgOf("Namics AG"));
		vcard.addOrganization(orgOf("Current Employer AG"));
		vcard.addOrganization(orgOf("Old Job GmbH"));

		// Production order: defunct organizations are removed first.
		new OrganizationRemovalRule(List.of("Namics")).apply(vcard);
		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getOrganizations().getFirst().getValues()).containsExactly("Current Employer AG");
	}

	@Test
	void handlesContactsWithoutOrganizations() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
