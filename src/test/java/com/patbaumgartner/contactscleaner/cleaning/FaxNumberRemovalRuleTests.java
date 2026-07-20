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
