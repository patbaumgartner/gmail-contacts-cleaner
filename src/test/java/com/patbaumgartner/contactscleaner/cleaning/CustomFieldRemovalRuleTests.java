package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

import ezvcard.VCard;
import ezvcard.property.RawProperty;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomFieldRemovalRuleTests {

	private final CustomFieldRemovalRule rule = new CustomFieldRemovalRule(List.of("Age"));

	private static VCard contactWithCustomField(String label, String value) {
		VCard vcard = new VCard();
		RawProperty abLabel = vcard.addExtendedProperty("X-ABLabel", label);
		abLabel.setGroup("item1");
		RawProperty custom = vcard.addExtendedProperty("X-ABCustom", value);
		custom.setGroup("item1");
		return vcard;
	}

	@Test
	void removesTheWholeLabeledGroup() {
		VCard vcard = contactWithCustomField("Age", "42");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@Test
	void labelMatchingIsCaseInsensitive() {
		assertThat(this.rule.apply(contactWithCustomField("AGE", "42"))).isTrue();
		assertThat(this.rule.apply(contactWithCustomField(" age ", "42"))).isTrue();
	}

	@Test
	void keepsOtherCustomFields() {
		VCard vcard = contactWithCustomField("Nickname at work", "JD");

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getExtendedProperties()).hasSize(2);
	}

	@Test
	void removesStandaloneXProperties() {
		VCard vcard = new VCard();
		vcard.addExtendedProperty("X-AGE", "42");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@Test
	void removesGroupedStandardPropertiesOfTheLabeledGroup() {
		VCard vcard = new VCard();
		RawProperty abLabel = vcard.addExtendedProperty("X-ABLabel", "Age");
		abLabel.setGroup("item2");
		ezvcard.property.Url url = vcard.addUrl("https://example.com/age-page");
		url.setGroup("item2");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getUrls()).isEmpty();
		assertThat(vcard.getExtendedProperties()).isEmpty();
	}

	@Test
	void emptyLabelListDisablesTheRule() {
		var disabled = new CustomFieldRemovalRule(List.of());

		assertThat(disabled.apply(contactWithCustomField("Age", "42"))).isFalse();
	}

	@Test
	void handlesContactsWithoutCustomFields() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
