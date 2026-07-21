package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Address;
import ezvcard.property.Email;
import ezvcard.property.Organization;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmptyPropertyRemovalRuleTests {

	private final EmptyPropertyRemovalRule rule = new EmptyPropertyRemovalRule();

	@Test
	void removesBlankTelEmailUrlAndNoteProperties() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("  "));
		vcard.addTelephoneNumber(new Telephone("+41446681800"));
		vcard.addEmail(new Email(""));
		vcard.addEmail(new Email("jane.doe@gmail.com"));
		vcard.addUrl("");
		vcard.addNote("   ");

		assertThat(rule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41446681800");
		assertThat(vcard.getEmails()).extracting(Email::getValue).containsExactly("jane.doe@gmail.com");
		assertThat(vcard.getUrls()).isEmpty();
		assertThat(vcard.getNotes()).isEmpty();
	}

	@Test
	void removesAllBlankOrganizations() {
		VCard vcard = new VCard();
		Organization blankOrg = new Organization();
		blankOrg.getValues().add("");
		blankOrg.getValues().add("  ");
		vcard.addOrganization(blankOrg);
		Organization realOrg = new Organization();
		realOrg.getValues().add("ACME Corp");
		vcard.addOrganization(realOrg);

		assertThat(rule.apply(vcard)).isTrue();
		assertThat(vcard.getOrganizations()).containsExactly(realOrg);
	}

	@Test
	void removesAllBlankAddresses() {
		VCard vcard = new VCard();
		vcard.addAddress(new Address());
		Address completeAddress = new Address();
		completeAddress.setStreetAddress("Bahnhofstrasse 1");
		completeAddress.setLocality("Zurich");
		vcard.addAddress(completeAddress);

		assertThat(rule.apply(vcard)).isTrue();
		assertThat(vcard.getAddresses()).containsExactly(completeAddress);
	}

	@Test
	void removesAddressesWithOnlyCityOrCountry() {
		VCard vcard = new VCard();
		Address cityOnly = new Address();
		cityOnly.setLocality("Zurich");
		vcard.addAddress(cityOnly);
		Address countryOnly = new Address();
		countryOnly.setCountry("Switzerland");
		vcard.addAddress(countryOnly);
		Address cityAndCountry = new Address();
		cityAndCountry.setLocality("Zurich");
		cityAndCountry.setCountry("Switzerland");
		vcard.addAddress(cityAndCountry);
		Address postalAddress = new Address();
		postalAddress.setPostalCode("8001");
		postalAddress.setLocality("Zurich");
		vcard.addAddress(postalAddress);

		assertThat(rule.apply(vcard)).isTrue();
		assertThat(vcard.getAddresses()).containsExactly(postalAddress);
	}

	@Test
	void removesBlankAndDuplicateExtendedProperties() {
		VCard vcard = new VCard();
		vcard.addExtendedProperty("X-ABLABEL", "");
		vcard.addExtendedProperty("X-GENDER", "Male");
		vcard.addExtendedProperty("X-GENDER", "Male");
		vcard.addExtendedProperty("X-GENDER", "Male");

		assertThat(rule.apply(vcard)).isTrue();
		assertThat(vcard.getExtendedProperties()).hasSize(1);
		assertThat(vcard.getExtendedProperties().getFirst().getValue()).isEqualTo("Male");
	}

	@Test
	void reportsNoChangeForCleanContact() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("+41446681800"));
		vcard.addEmail(new Email("jane.doe@gmail.com"));

		assertThat(rule.apply(vcard)).isFalse();
	}

}
