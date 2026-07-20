package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Address;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class GeoCoordinateAddressRemovalRuleTests {

	private final GeoCoordinateAddressRemovalRule rule = new GeoCoordinateAddressRemovalRule();

	private static VCard contactWithStreet(String street) {
		VCard vcard = new VCard();
		Address address = new Address();
		address.setStreetAddress(street);
		vcard.addAddress(address);
		return vcard;
	}

	@ParameterizedTest
	@ValueSource(strings = { "47.392977,8.475039", "37.3317, -122.0307", "-33.8688, 151.2093" })
	void removesCoordinateOnlyAddresses(String coordinates) {
		VCard vcard = contactWithStreet(coordinates);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getAddresses()).isEmpty();
	}

	@Test
	void coordinatesSplitAcrossComponentsAreCaughtToo() {
		VCard vcard = new VCard();
		Address address = new Address();
		address.setStreetAddress("47.392977,");
		address.setLocality("8.475039");
		vcard.addAddress(address);

		assertThat(this.rule.apply(vcard)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Bahnhofstrasse 1", "8. Avenue 47", "Route 66" })
	void keepsRealStreets(String street) {
		VCard vcard = contactWithStreet(street);

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getAddresses()).hasSize(1);
	}

	@Test
	void addressWithCoordinatesAndCityIsKept() {
		VCard vcard = new VCard();
		Address address = new Address();
		address.setStreetAddress("47.392977,8.475039");
		address.setLocality("Zürich");
		vcard.addAddress(address);

		assertThat(this.rule.apply(vcard)).isFalse();
	}

	@Test
	void handlesContactsWithoutAddresses() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
