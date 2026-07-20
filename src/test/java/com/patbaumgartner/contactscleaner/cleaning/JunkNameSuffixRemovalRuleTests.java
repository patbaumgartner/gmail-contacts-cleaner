package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.StructuredName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JunkNameSuffixRemovalRuleTests {

	private final JunkNameSuffixRemovalRule rule = new JunkNameSuffixRemovalRule();

	private static VCard contactWithSuffix(String suffix) {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven("Jane");
		name.setFamily("Doe");
		name.getSuffixes().add(suffix);
		vcard.setStructuredName(name);
		return vcard;
	}

	@ParameterizedTest
	@ValueSource(strings = { "(JIRA)", "(bkrt)", "(Tianyun Gift)", "(D3\\GASTRO\\CATE SERV)", "[import]", "{whatsapp}",
			" (etro) " })
	void removesParenthesizedImportJunk(String suffix) {
		VCard vcard = contactWithSuffix(suffix);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getSuffixes()).isEmpty();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Jr.", "PMP", "MSc", "PhD", "III" })
	void keepsRealHonorificSuffixes(String suffix) {
		VCard vcard = contactWithSuffix(suffix);

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getStructuredName().getSuffixes()).containsExactly(suffix);
	}

	@Test
	void handlesContactsWithoutSuffixes() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
