package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Set;

import ezvcard.VCard;
import ezvcard.property.Email;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailDomainRemovalRuleTests {

	private final EmailDomainRemovalRule rule = new EmailDomainRemovalRule(Set.of("former.example", "legacy.test"));

	@Test
	void removesAddressesAtConfiguredDomainsAndTheirSubdomains() {
		VCard vcard = new VCard();
		vcard.addEmail(new Email("jane@former.example"));
		vcard.addEmail(new Email("jane@eu.legacy.test"));
		vcard.addEmail(new Email("jane@example.com"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails()).extracting(Email::getValue).containsExactly("jane@example.com");
	}

	@Test
	void keepsLookalikeDomainsAndReportsNoChange() {
		VCard vcard = new VCard();
		vcard.addEmail(new Email("jane@notformer.example"));
		vcard.addEmail(new Email("jane@legacy.test.example"));

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getEmails()).hasSize(2);
	}

	@Test
	void matchesDomainsCaseInsensitively() {
		VCard vcard = new VCard();
		vcard.addEmail(new Email("jane@FORMER.EXAMPLE"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails()).isEmpty();
	}

}