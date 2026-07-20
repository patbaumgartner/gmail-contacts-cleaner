package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Address;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedundantAddressRemovalRuleTests {

	private final RedundantAddressRemovalRule rule = new RedundantAddressRemovalRule();

	private static Address address(String street, String locality, String postalCode, String country) {
		Address address = new Address();
		address.setStreetAddress(street);
		address.setLocality(locality);
		address.setPostalCode(postalCode);
		address.setCountry(country);
		return address;
	}

	@Test
	void keepsTheRicherOfTwoRedundantAddresses() {
		VCard vcard = new VCard();
		Address poor = address("Bahnhofstrasse 1", "Zürich", null, null);
		Address rich = address("Bahnhofstrasse 1", "Zürich", "8001", "Switzerland");
		vcard.addAddress(poor);
		vcard.addAddress(rich);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getAddresses()).containsExactly(rich);
	}

	@Test
	void richerAddressSurvivesRegardlessOfOrder() {
		VCard vcard = new VCard();
		Address rich = address("Bahnhofstrasse 1", "Zürich", "8001", "Switzerland");
		Address poor = address("Bahnhofstrasse 1", "Zürich", null, null);
		vcard.addAddress(rich);
		vcard.addAddress(poor);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getAddresses()).containsExactly(rich);
	}

	@Test
	void comparisonIgnoresCaseAndWhitespace() {
		VCard vcard = new VCard();
		Address poor = address("bahnhofstrasse  1", "ZÜRICH", null, null);
		Address rich = address("Bahnhofstrasse 1", "Zürich", "8001", null);
		vcard.addAddress(poor);
		vcard.addAddress(rich);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getAddresses()).containsExactly(rich);
	}

	@Test
	void removesExactDuplicatesKeepingTheFirst() {
		VCard vcard = new VCard();
		Address first = address("Bahnhofstrasse 1", "Zürich", "8001", "Switzerland");
		Address second = address("Bahnhofstrasse 1", "Zürich", "8001", "Switzerland");
		vcard.addAddress(first);
		vcard.addAddress(second);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getAddresses()).hasSize(1);
	}

	@Test
	void neverTouchesGenuinelyDifferentAddresses() {
		VCard vcard = new VCard();
		vcard.addAddress(address("Bahnhofstrasse 1", "Zürich", "8001", "Switzerland"));
		vcard.addAddress(address("Hauptstrasse 5", "Bern", "3011", "Switzerland"));

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getAddresses()).hasSize(2);
	}

	@Test
	void conflictingComponentsPreventRemoval() {
		VCard vcard = new VCard();
		// Same street, different postal codes — not redundant, keep both.
		vcard.addAddress(address("Bahnhofstrasse 1", "Zürich", "8001", null));
		vcard.addAddress(address("Bahnhofstrasse 1", "Zürich", "8002", null));

		assertThat(this.rule.apply(vcard)).isFalse();
	}

	@Test
	void handlesContactsWithFewerThanTwoAddresses() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
