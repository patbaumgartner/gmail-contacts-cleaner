package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NameTrimmingRuleTests {

	private final NameTrimmingRule rule = new NameTrimmingRule();

	@Test
	void trimsAllNameComponents() {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven("  Jane ");
		name.setFamily(" Doe  ");
		name.getAdditionalNames().add(" Maria ");
		vcard.setStructuredName(name);
		vcard.setFormattedName(new FormattedName("  Jane Maria Doe "));

		assertThat(rule.apply(vcard)).isTrue();
		assertThat(name.getGiven()).isEqualTo("Jane");
		assertThat(name.getFamily()).isEqualTo("Doe");
		assertThat(name.getAdditionalNames()).containsExactly("Maria");
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Jane Maria Doe");
	}

	@Test
	void reportsNoChangeForCleanNames() {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven("Jane");
		name.setFamily("Doe");
		vcard.setStructuredName(name);
		vcard.setFormattedName(new FormattedName("Jane Doe"));

		assertThat(rule.apply(vcard)).isFalse();
	}

	@Test
	void handlesContactsWithoutNames() {
		assertThat(rule.apply(new VCard())).isFalse();
	}

	@Test
	void handlesPartialNames() {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven(" OnlyGiven ");
		vcard.setStructuredName(name);

		assertThat(rule.apply(vcard)).isTrue();
		assertThat(name.getGiven()).isEqualTo("OnlyGiven");
		assertThat(name.getFamily()).isNull();
	}

}
