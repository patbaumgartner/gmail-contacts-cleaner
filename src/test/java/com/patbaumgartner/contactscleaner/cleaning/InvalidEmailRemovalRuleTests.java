package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Email;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class InvalidEmailRemovalRuleTests {

	private final InvalidEmailRemovalRule rule = new InvalidEmailRemovalRule();

	@ParameterizedTest
	@ValueSource(strings = { "not-an-email", "jane@", "@example.com", "jane doe@example.com", "jane@localhost",
			"jane@@example.com", "+41791234567", "jane@example" })
	void removesSyntacticallyInvalidAddresses(String invalid) {
		VCard vcard = new VCard();
		vcard.addEmail(new Email(invalid));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "jane.doe@gmail.com", "jane+news@example.co.uk", "j_d-1@sub.example.io",
			"jane.doe@xn--mnchen-3ya.de" })
	void keepsValidAddresses(String valid) {
		VCard vcard = new VCard();
		vcard.addEmail(new Email(valid));

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getEmails()).hasSize(1);
	}

	@Test
	void removesOnlyTheInvalidAddressOfAContact() {
		VCard vcard = new VCard();
		vcard.addEmail(new Email("jane.doe@gmail.com"));
		vcard.addEmail(new Email("franz@"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails()).extracting(Email::getValue).containsExactly("jane.doe@gmail.com");
	}

}
