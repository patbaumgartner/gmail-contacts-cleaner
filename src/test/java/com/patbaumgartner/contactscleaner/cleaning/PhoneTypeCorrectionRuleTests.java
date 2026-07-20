package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneTypeCorrectionRuleTests {

	private final PhoneTypeCorrectionRule swissRule = new PhoneTypeCorrectionRule("CH");

	private static VCard contactWith(Telephone telephone) {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(telephone);
		return vcard;
	}

	private static Telephone phone(String number, TelephoneType... types) {
		Telephone telephone = new Telephone(number);
		for (TelephoneType type : types) {
			telephone.getTypes().add(type);
		}
		return telephone;
	}

	@Test
	void mobileNumberGainsTheCellType() {
		Telephone mobile = phone("+41791234567");
		assertThat(this.swissRule.apply(contactWith(mobile))).isTrue();
		assertThat(mobile.getTypes()).containsExactly(TelephoneType.CELL);
	}

	@Test
	void workMobileKeepsItsWorkContext() {
		Telephone workMobile = phone("+41791234567", TelephoneType.WORK);
		assertThat(this.swissRule.apply(contactWith(workMobile))).isTrue();
		assertThat(workMobile.getTypes()).containsExactlyInAnyOrder(TelephoneType.WORK, TelephoneType.CELL);
	}

	@Test
	void landlineLosesAWrongCellType() {
		Telephone landline = phone("+41446681800", TelephoneType.CELL, TelephoneType.WORK);
		assertThat(this.swissRule.apply(contactWith(landline))).isTrue();
		assertThat(landline.getTypes()).containsExactly(TelephoneType.WORK);
	}

	@Test
	void correctlyTypedNumbersAreNoChange() {
		assertThat(this.swissRule.apply(contactWith(phone("+41791234567", TelephoneType.CELL)))).isFalse();
		assertThat(this.swissRule.apply(contactWith(phone("+41446681800", TelephoneType.WORK)))).isFalse();
	}

	@Test
	void ambiguousNumberingPlansAreNeverJudged() {
		// US numbers are FIXED_LINE_OR_MOBILE — the label could be right either way.
		assertThat(this.swissRule.apply(contactWith(phone("+16505550123", TelephoneType.CELL)))).isFalse();
		assertThat(this.swissRule.apply(contactWith(phone("+16505550123")))).isFalse();
	}

	@Test
	void nationalNumbersRequireARegion() {
		var regionless = new PhoneTypeCorrectionRule("");
		assertThat(regionless.apply(contactWith(phone("0791234567")))).isFalse();
		// International numbers are always judgeable.
		Telephone mobile = phone("+41791234567");
		assertThat(regionless.apply(contactWith(mobile))).isTrue();
	}

	@Test
	void invalidNumbersAreIgnored() {
		assertThat(this.swissRule.apply(contactWith(phone("*133#")))).isFalse();
		assertThat(this.swissRule.apply(contactWith(phone("+4144")))).isFalse();
	}

}
