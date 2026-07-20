package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Email;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailRulesTests {

	private final EmailNormalizationRule normalizationRule = new EmailNormalizationRule();

	private final DuplicateEmailRemovalRule deduplicationRule = new DuplicateEmailRemovalRule();

	@Test
	void lowerCasesAndTrimsAddresses() {
		VCard vcard = new VCard();
		vcard.addEmail(new Email("  Jane.Doe@GMAIL.com "));

		assertThat(normalizationRule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails()).extracting(Email::getValue).containsExactly("jane.doe@gmail.com");
	}

	@Test
	void reportsNoChangeForAlreadyNormalizedAddresses() {
		VCard vcard = new VCard();
		vcard.addEmail(new Email("jane.doe@gmail.com"));

		assertThat(normalizationRule.apply(vcard)).isFalse();
	}

	@Test
	void removesDuplicatesAfterNormalization() {
		VCard vcard = new VCard();
		vcard.addEmail(new Email("Jane.Doe@gmail.com"));
		vcard.addEmail(new Email("jane.doe@gmail.com "));
		vcard.addEmail(new Email("jane@work.example"));

		normalizationRule.apply(vcard);
		assertThat(deduplicationRule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails()).extracting(Email::getValue)
			.containsExactly("jane.doe@gmail.com", "jane@work.example");
	}

	@Test
	void handlesContactWithoutEmailAddresses() {
		assertThat(normalizationRule.apply(new VCard())).isFalse();
		assertThat(deduplicationRule.apply(new VCard())).isFalse();
	}

}
