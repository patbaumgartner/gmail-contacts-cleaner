package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Email;
import ezvcard.property.FormattedName;
import ezvcard.property.StructuredName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class NameRepairRuleTests {

	private final NameRepairRule rule = new NameRepairRule();

	private static VCard named(String given, String family) {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven(given);
		name.setFamily(family);
		vcard.setStructuredName(name);
		vcard.setFormattedName(new FormattedName((given + " " + family).trim()));
		return vcard;
	}

	// ── ALL-CAPS ──────────────────────────────────────────────────────────────

	@ParameterizedTest(name = "{0} {1} -> {2} {3}")
	@CsvSource(textBlock = """
			JANE,      DOE,           Jane,      Doe
			JEAN-LUC,  PICARD,        Jean-Luc,  Picard
			RONALD,    MCDONALD,      Ronald,    McDonald
			SHANE,     O'BRIEN,       Shane,     O'Brien
			JANE,      VAN DER BERG,  Jane,      van der Berg
			""")
	void repairsAllCapsNamesWithSmartCasing(String given, String family, String expectedGiven, String expectedFamily) {
		VCard vcard = named(given, family);

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo(expectedGiven);
		assertThat(vcard.getStructuredName().getFamily()).isEqualTo(expectedFamily);
	}

	@Test
	void mixedCaseNamesAreNeverTouched() {
		assertThat(this.rule.apply(named("Jane", "Doe"))).isFalse();
		assertThat(this.rule.apply(named("Jane", "McDonald"))).isFalse();
		// Short acronym-like names are left alone (could be initials).
		assertThat(this.rule.apply(named("JD", "NG"))).isFalse();
	}

	// ── Prefixes ──────────────────────────────────────────────────────────────

	@Test
	void canonicalizesKnownPrefixes() {
		VCard vcard = named("Jane", "Doe");
		vcard.getStructuredName().getPrefixes().add("Dr");

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getPrefixes()).containsExactly("Dr.");
	}

	@Test
	void leavesCanonicalAndUnknownPrefixesAlone() {
		VCard canonical = named("Jane", "Doe");
		canonical.getStructuredName().getPrefixes().add("Dr.");
		assertThat(this.rule.apply(canonical)).isFalse();

		VCard unknown = named("Jane", "Doe");
		unknown.getStructuredName().getPrefixes().add("Sir");
		assertThat(this.rule.apply(unknown)).isFalse();
	}

	// ── Quoted names ──────────────────────────────────────────────────────────

	@Test
	void stripsWrappingQuotesFromNames() {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven("\"Jane\"");
		name.setFamily("Doe");
		vcard.setStructuredName(name);
		vcard.setFormattedName(new FormattedName("\"Jane Doe\""));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Jane Doe");
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Jane");
	}

	@Test
	void stripsTypographicQuotesToo() {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName("\u201cJane Doe\u201d"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Jane Doe");
	}

	@Test
	void innerNicknameQuotesAreKept() {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName("Patrick \"Pat\" Miller"));

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Patrick \"Pat\" Miller");
	}

	@Test
	void stripsOneSidedStrayQuotes() {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName("\"Jane Doe"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Jane Doe");
	}

	@Test
	void removesEmojisAndInvisibleCharacters() {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven("Jane \uD83C\uDF38"); // 🌸
		name.setFamily("Doe\u200D"); // zero-width joiner
		vcard.setStructuredName(name);
		vcard.setFormattedName(new FormattedName("Jane \uD83C\uDF38 Doe \u2764\uFE0F")); // 🌸
																							// +
																							// ❤️

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Jane");
		assertThat(vcard.getStructuredName().getFamily()).isEqualTo("Doe");
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Jane Doe");
	}

	@Test
	void sanitizesQuotedSuffixesAndCollapsesWhitespace() {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven("Jane");
		name.setFamily("Doe");
		name.getSuffixes().add("'PMP'");
		vcard.setStructuredName(name);
		vcard.setFormattedName(new FormattedName("Jane   Doe"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getStructuredName().getSuffixes()).containsExactly("PMP");
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Jane Doe");
	}

	@Test
	void quoteOnlyNamesAreNotEmptied() {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName("\"\""));

		assertThat(this.rule.apply(vcard)).isFalse();
	}

	// ── "Last, First" display names ───────────────────────────────────────────

	@Test
	void flipsCommaFormattedDisplayNames() {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName("Muster, Max"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Max Muster");
		// Empty structured name is populated from the parts.
		assertThat(vcard.getStructuredName().getGiven()).isEqualTo("Max");
		assertThat(vcard.getStructuredName().getFamily()).isEqualTo("Muster");
	}

	@Test
	void commaFormAgreeingWithTheStructuredNameIsRepaired() {
		VCard vcard = named("Max", "Muster");
		vcard.setFormattedName(new FormattedName("Muster, Max"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Max Muster");
	}

	@Test
	void structuredNameContradictionPreventsTheFlip() {
		// N says given=Muster family=Max — ambiguous, do not touch.
		VCard vcard = named("Muster", "Max");
		vcard.setFormattedName(new FormattedName("Muster, Max"));

		assertThat(this.rule.apply(vcard)).isFalse();
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Muster, Max");
	}

	@Test
	void multipleCommasAndCompanyStyleNamesAreLeftAlone() {
		VCard multi = new VCard();
		multi.setFormattedName(new FormattedName("Doe, Jane, PhD"));
		assertThat(this.rule.apply(multi)).isFalse();

		VCard company = new VCard();
		company.setFormattedName(new FormattedName("Meier, Müller & Partner AG"));
		assertThat(this.rule.apply(company)).isFalse();
		assertThat(company.getFormattedName().getValue()).isEqualTo("Meier, Müller & Partner AG");
	}

	// ── E-mail in name ────────────────────────────────────────────────────────

	@Test
	void movesEmailFromNameToEmailsAndDerivesAReadableName() {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven("jane.doe@example.com");
		vcard.setStructuredName(name);
		vcard.setFormattedName(new FormattedName("jane.doe@example.com"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails()).extracting(Email::getValue).containsExactly("jane.doe@example.com");
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Jane Doe");
	}

	@Test
	void doesNotDuplicateAnAlreadyKnownEmail() {
		VCard vcard = new VCard();
		StructuredName name = new StructuredName();
		name.setGiven("jane.doe@example.com");
		name.setFamily("Doe");
		vcard.setStructuredName(name);
		vcard.addEmail(new Email("jane.doe@example.com"));

		assertThat(this.rule.apply(vcard)).isTrue();
		assertThat(vcard.getEmails()).hasSize(1);
		assertThat(vcard.getStructuredName().getGiven()).isNull();
		assertThat(vcard.getFormattedName().getValue()).isEqualTo("Doe");
	}

	@Test
	void handlesContactsWithoutNames() {
		assertThat(this.rule.apply(new VCard())).isFalse();
	}

}
