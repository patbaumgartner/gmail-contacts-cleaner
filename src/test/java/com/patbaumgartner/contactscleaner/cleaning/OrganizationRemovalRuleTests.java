package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

import ezvcard.VCard;
import ezvcard.property.Organization;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationRemovalRuleTests {

	private final OrganizationRemovalRule rule = new OrganizationRemovalRule(List.of("Acme"));

	private static VCard contactAt(String organization) {
		VCard vcard = new VCard();
		vcard.setOrganization(organization);
		return vcard;
	}

	@ParameterizedTest
	@ValueSource(strings = { "Acme", "acme", "Acme AG", "Acme (a Merkle company)", "  Acme  " })
	void removesDefunctOrganizationsByPrefix(String organization) {
		VCard vcard = contactAt(organization);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getOrganizations()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Acmesson Consulting", "ACME Corp", "Not Acme" })
	void keepsOtherOrganizations(String organization) {
		VCard vcard = contactAt(organization);

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getOrganizations()).hasSize(1);
	}

	@Test
	void emptyListDisablesTheRule() {
		var disabled = new OrganizationRemovalRule(List.of());

		assertThat(disabled.apply(contactAt("Acme AG"))).isFalse();
	}

	@Test
	void removesOnlyTheMatchingOrganization() {
		VCard vcard = new VCard();
		vcard.addOrganization(orgOf("Acme AG"));
		vcard.addOrganization(orgOf("Merkle"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getOrganizations()).hasSize(1);
		assertThat(vcard.getOrganizations().getFirst().getValues()).containsExactly("Merkle");
	}

	@Test
	void handlesContactsWithoutOrganizations() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

	private static Organization orgOf(String name) {
		Organization organization = new Organization();
		organization.getValues().add(name);
		return organization;
	}

}
