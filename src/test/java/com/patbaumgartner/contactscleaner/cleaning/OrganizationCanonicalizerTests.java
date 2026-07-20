package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

import ezvcard.VCard;
import ezvcard.property.FormattedName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrganizationCanonicalizerTests {

	private final OrganizationCanonicalizer canonicalizer = new OrganizationCanonicalizer(
			CleaningProperties.defaults());

	private static VCard contactAt(String organization) {
		VCard vcard = new VCard();
		vcard.setFormattedName(new FormattedName("Jane Doe"));
		vcard.setOrganization(organization);
		return vcard;
	}

	private static String orgOf(VCard vcard) {
		return vcard.getOrganizations().getFirst().getValues().getFirst();
	}

	@Test
	void mostFrequentSpellingWins() {
		VCard first = contactAt("Namics AG");
		VCard second = contactAt("Namics AG");
		VCard third = contactAt("namics AG");
		VCard fourth = contactAt("Namics GmbH");

		var changed = this.canonicalizer.canonicalize(List.of(first, second, third, fourth));

		assertThat(changed).containsExactlyInAnyOrder(third, fourth);
		assertThat(List.of(first, second, third, fourth))
			.allSatisfy((vcard) -> assertThat(orgOf(vcard)).isEqualTo("Namics AG"));
	}

	@Test
	void tieBreaksToTheLongestVariant() {
		VCard bare = contactAt("Trivadis");
		VCard withSuffix = contactAt("Trivadis AG");

		this.canonicalizer.canonicalize(List.of(bare, withSuffix));

		assertThat(orgOf(bare)).isEqualTo("Trivadis AG");
		assertThat(orgOf(withSuffix)).isEqualTo("Trivadis AG");
	}

	@Test
	void distinctCompaniesAreNeverTouched() {
		VCard acme = contactAt("ACME Corp");
		VCard other = contactAt("Other Inc");

		assertThat(this.canonicalizer.canonicalize(List.of(acme, other))).isEmpty();
		assertThat(orgOf(acme)).isEqualTo("ACME Corp");
	}

	@Test
	void singleConsistentSpellingIsNoChange() {
		VCard first = contactAt("Netcetera AG");
		VCard second = contactAt("Netcetera AG");

		assertThat(this.canonicalizer.canonicalize(List.of(first, second))).isEmpty();
	}

	@Test
	void punctuationVariantsCollapse() {
		VCard plain = contactAt("Pivotal Inc.");
		VCard comma = contactAt("Pivotal, Inc.");
		VCard bare = contactAt("Pivotal");
		VCard fourth = contactAt("Pivotal, Inc.");

		this.canonicalizer.canonicalize(List.of(plain, comma, bare, fourth));

		assertThat(orgOf(bare)).isEqualTo("Pivotal, Inc.");
	}

	@Test
	void disabledViaProperty() {
		var disabled = new OrganizationCanonicalizer(new CleaningProperties(true, "", true, false, false, true, true,
				true, false, true, true, true, true, true, true, true, true, true, true, true, java.util.List.of("Age"),
				java.util.List.of(), true, true, false, false, 2, false, false));
		VCard first = contactAt("Namics AG");
		VCard second = contactAt("namics ag");

		assertThat(disabled.canonicalize(List.of(first, second))).isEmpty();
	}

}
