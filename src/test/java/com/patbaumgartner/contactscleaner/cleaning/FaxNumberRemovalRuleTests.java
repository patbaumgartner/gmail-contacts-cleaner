package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FaxNumberRemovalRuleTests {

	private final FaxNumberRemovalRule rule = new FaxNumberRemovalRule();

	private static Telephone phone(String number, TelephoneType... types) {
		Telephone telephone = new Telephone(number);
		for (TelephoneType type : types) {
			telephone.getTypes().add(type);
		}
		return telephone;
	}

	@Test
	void removesWorkAndHomeFaxNumbers() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(phone("+41446681801", TelephoneType.FAX, TelephoneType.WORK));
		vcard.addTelephoneNumber(phone("+41446681802", TelephoneType.FAX, TelephoneType.HOME));
		vcard.addTelephoneNumber(phone("+41791234567", TelephoneType.CELL));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41791234567");
	}

	@Test
	void removesCustomLabeledFaxNumbersWithTheirLabel() {
		VCard vcard = new VCard();
		Telephone fax = new Telephone("+41446681801");
		fax.setGroup("item1");
		vcard.addTelephoneNumber(fax);
		ezvcard.property.RawProperty label = vcard.addExtendedProperty("X-ABLabel", "Work Fax");
		label.setGroup("item1");
		vcard.addTelephoneNumber(phone("+41791234567", TelephoneType.CELL));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41791234567");
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@Test
	void recognizesGermanTelefaxLabels() {
		VCard vcard = new VCard();
		Telephone fax = new Telephone("+41446681801");
		fax.setGroup("item2");
		vcard.addTelephoneNumber(fax);
		vcard.addExtendedProperty("X-ABLabel", "Telefax Büro").setGroup("item2");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getTelephoneNumbers()).isEmpty();
	}

	@Test
	void nonFaxCustomLabelsAreKept() {
		VCard vcard = new VCard();
		Telephone direct = new Telephone("+41446681800");
		direct.setGroup("item3");
		vcard.addTelephoneNumber(direct);
		vcard.addExtendedProperty("X-ABLabel", "Direct line").setGroup("item3");

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getTelephoneNumbers()).hasSize(1);
	}

	@Test
	void keepsRegularNumbersUntouched() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(phone("+41791234567", TelephoneType.CELL));
		vcard.addTelephoneNumber(phone("+41446681800", TelephoneType.WORK));

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getTelephoneNumbers()).hasSize(2);
	}

	@Test
	void handlesContactsWithoutPhoneNumbers() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
