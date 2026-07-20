package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Telephone;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E.164 formatting with a configured default phone region (libphonenumber).
 */
class PhoneNumberE164Tests {

	private final PhoneNumberNormalizationRule swissRule = new PhoneNumberNormalizationRule("CH");

	@ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
	@CsvSource(textBlock = """
			'044 668 18 00',        '+41446681800'
			'0446681800',           '+41446681800'
			'079 123 45 67',        '+41791234567'
			'0041 44 668 18 00',    '+41446681800'
			'+41 44 668 18 00',     '+41446681800'
			'+49 30 901820',        '+4930901820'
			""")
	void formatsNationalAndInternationalNumbersToE164(String input, String expected) {
		assertThat(this.swissRule.normalize(input)).isEqualTo(expected);
	}

	@ParameterizedTest(name = "keeps \"{0}\" as \"{1}\"")
	@CsvSource(textBlock = """
			'117',            '117'
			'MAILBOX',        'MAILBOX'
			'12',             '12'
			""")
	void keepsUnparseableOrInvalidNumbersStrippedButOtherwiseUntouched(String input, String expected) {
		assertThat(this.swissRule.normalize(input)).isEqualTo(expected);
	}

	@Test
	void stripsInvisibleUnicodeFormatCharactersAndDoublePlus() {
		// U+202A (LTR embedding) and U+202C (pop direction) — WhatsApp copy-paste debris
		assertThat(this.swissRule.normalize("\u202a+41 78 710 52 71\u202c")).isEqualTo("+41787105271");
		assertThat(this.swissRule.normalize("++41 27 474 60 31")).isEqualTo("+41274746031");
	}

	@Test
	void lowerCaseRegionIsAccepted() {
		var rule = new PhoneNumberNormalizationRule("ch");
		assertThat(rule.normalize("044 668 18 00")).isEqualTo("+41446681800");
	}

	@Test
	void collapsesDifferentRepresentationsSoDeduplicationCatchesThem() {
		VCard vcard = new VCard();
		vcard.addTelephoneNumber(new Telephone("044 668 18 00"));
		vcard.addTelephoneNumber(new Telephone("+41446681800"));
		vcard.addTelephoneNumber(new Telephone("0041 44 668 18 00"));

		this.swissRule.apply(vcard);
		new DuplicatePhoneNumberRemovalRule().apply(vcard);

		assertThat(vcard.getTelephoneNumbers()).extracting(Telephone::getText).containsExactly("+41446681800");
	}

}
