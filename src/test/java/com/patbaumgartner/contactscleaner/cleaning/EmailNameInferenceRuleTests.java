package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmailNameInferenceRuleTests {

	private final EmailNameInferenceRule rule = new EmailNameInferenceRule();

	@Test
	void infersStructuredNameFromDotSeparatedEmail() {
		VCard vcard = new VCard();
		vcard.addEmail(new Email("jane.doe@example.com"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Jane");
		assertThat(vcard.getStructuredName().getFamily()).isEqualTo("Doe");
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Jane Doe");
	}

	@Test
	void fillsOnlyTheMissingNameComponentFromUnderscoreSeparatedEmail() {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven("Janet");
		vcard.setStructuredName(name);
		vcard.setFormattedName(new FormattedName("Janet Example"));
		vcard.addEmail(new Email("jane_doe@example.com"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Janet");
		assertThat(vcard.getStructuredName().getFamily()).isEqualTo("Doe");
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Janet Example");
	}

	@Test
	void ignoresAmbiguousAndNonPersonEmailLocalParts() {
		VCard multiPart = new VCard();
		multiPart.addEmail(new Email("jane.middle.doe@example.com"));
		assertThat(this.rule.apply(multiPart)).isFalse();

		VCard conflicting = new VCard();
		conflicting.addEmail(new Email("jane.doe@example.com"));
		conflicting.addEmail(new Email("john.smith@example.com"));
		assertThat(this.rule.apply(conflicting)).isFalse();
	}

}