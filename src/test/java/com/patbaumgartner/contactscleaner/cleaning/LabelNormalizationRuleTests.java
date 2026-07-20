package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.parameter.AddressType;
import ezvcard.parameter.EmailType;
import ezvcard.property.Address;
import ezvcard.property.Email;
import ezvcard.property.RawProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LabelNormalizationRuleTests {

	private final LabelNormalizationRule rule = new LabelNormalizationRule();

	private static VCard emailWithLabel(String label) {
		VCard vcard = new VCard();
		Email email = new Email("jane.doe@example.com");
		email.setGroup("item1");
		vcard.addEmail(email);
		vcard.addExtendedProperty("X-ABLabel", label).setGroup("item1");
		return vcard;
	}

	@ParameterizedTest
	@ValueSource(strings = { "Internet email", "* Internet Email", "Obsolete", "Conference", "Sonstige", "School" })
	void dropsCustomLabelsInFavorOfTheDefaultType(String label) {
		VCard vcard = emailWithLabel(label);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails().getFirst().getTypes()).isEmpty();
		assertThat(vcard.getEmails().getFirst().getGroup()).isNull();
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Work", "* Work", "Büro", "Geschäftlich", "business" })
	void mapsWorkLabelsToTheStandardWorkType(String label) {
		VCard vcard = emailWithLabel(label);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails().getFirst().getTypes()).containsExactly(EmailType.WORK);
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Home", "* Home", "Privat", "zuhause" })
	void mapsHomeLabelsToTheStandardHomeType(String label) {
		VCard vcard = emailWithLabel(label);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails().getFirst().getTypes()).containsExactly(EmailType.HOME);
	}

	@Test
	void normalizesAddressLabelsToo() {
		VCard vcard = new VCard();
		Address address = new Address();
		address.setLocality("Zürich");
		address.setGroup("item2");
		vcard.addAddress(address);
		vcard.addExtendedProperty("X-ABLabel", "Geschäftlich").setGroup("item2");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getAddresses().getFirst().getTypes()).containsExactly(AddressType.WORK);
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@Test
	void normalizesUrlLabels() {
		VCard vcard = new VCard();
		ezvcard.property.Url url = vcard.addUrl("https://example.com/blog");
		url.setGroup("item4");
		vcard.addExtendedProperty("X-ABLabel", "Blog").setGroup("item4");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getUrls().getFirst().getGroup()).isNull();
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@Test
	void mapsWorkUrlLabelsToTheWorkType() {
		VCard vcard = new VCard();
		ezvcard.property.Url url = vcard.addUrl("https://company.example");
		url.setGroup("item5");
		vcard.addExtendedProperty("X-ABLabel", "Work").setGroup("item5");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getUrls().getFirst().getType()).isEqualTo("work");
	}

	@Test
	void sweepsOrphanedLabelsWhoseGroupHasNoPropertyLeft() {
		VCard vcard = new VCard();
		// The labeled URL was removed by the URL cleanup — only the label remains.
		vcard.addExtendedProperty("X-ABLabel", "Klout").setGroup("item6");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@Test
	void dropsUnknownPhoneLabelsForTheDefaultType() {
		VCard vcard = new VCard();
		ezvcard.property.Telephone telephone = new ezvcard.property.Telephone("+41446681800");
		telephone.setGroup("item3");
		vcard.addTelephoneNumber(telephone);
		vcard.addExtendedProperty("X-ABLabel", "WhatsApp").setGroup("item3");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers().getFirst().getTypes()).isEmpty();
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@Test
	void mapsMobilePhoneLabelsToTheCellType() {
		VCard vcard = new VCard();
		ezvcard.property.Telephone telephone = new ezvcard.property.Telephone("+41791234567");
		telephone.setGroup("item7");
		vcard.addTelephoneNumber(telephone);
		vcard.addExtendedProperty("X-ABLabel", "Mobil").setGroup("item7");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers().getFirst().getTypes())
			.containsExactly(ezvcard.parameter.TelephoneType.CELL);
	}

	@Test
	void faxLabelsBecomeTheFaxTypeSoTheFaxRuleStillCatchesThem() {
		VCard vcard = new VCard();
		ezvcard.property.Telephone telephone = new ezvcard.property.Telephone("+41446681801");
		telephone.setGroup("item8");
		vcard.addTelephoneNumber(telephone);
		vcard.addExtendedProperty("X-ABLabel", "Work Fax").setGroup("item8");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers().getFirst().getTypes()).contains(ezvcard.parameter.TelephoneType.FAX,
				ezvcard.parameter.TelephoneType.WORK);

		// The fax rule now recognizes it by type.
		assertThat(new FaxNumberRemovalRule().apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers()).isEmpty();
	}

	@Test
	void handlesContactsWithoutLabels() {
		VCard vcard = new VCard();
		vcard.addEmail(new Email("jane.doe@example.com"));

		assertThat(this.rule.apply(vcard)).isFalse();
	}

}
