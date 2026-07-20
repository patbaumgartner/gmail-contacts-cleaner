package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.property.Organization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Cross-contact pass: canonicalizes organization name spellings. Years of manual entry
 * leave the same employer in half a dozen variants — {@code Acme}, {@code Acme AG},
 * {@code acme AG}, {@code Acme GmbH} — which breaks searching and grouping by
 * company.
 *
 * <p>
 * Organizations are grouped by a normalized key (lower-cased, punctuation and legal
 * suffixes like AG/GmbH/Inc/Ltd stripped); within each group the <strong>most frequent
 * original spelling</strong> wins and replaces the others. Frequency is the best
 * available proxy for the correct spelling — the variant most people typed. Note that
 * this intentionally folds legal-form variants (AG vs GmbH) of the same brand into one;
 * disable via {@code contacts-cleaner.cleaning.canonicalize-organizations=false} if you
 * track subsidiaries separately.
 */
@Component
public class OrganizationCanonicalizer {

	private static final Logger log = LoggerFactory.getLogger(OrganizationCanonicalizer.class);

	private static final java.util.regex.Pattern LEGAL_SUFFIXES = java.util.regex.Pattern
		.compile("\\b(ag|gmbh|inc|ltd|llc|sa|kg|co|plc|sarl|sagl)\\b\\.?");

	private final CleaningProperties properties;

	public OrganizationCanonicalizer(CleaningProperties properties) {
		this.properties = properties;
	}

	/**
	 * Rewrites organization spellings to the canonical variant, in place.
	 * @param vcards all contacts of one address book
	 * @return the contacts that were modified (identity-based), empty when disabled
	 */
	public Set<VCard> canonicalize(List<VCard> vcards) {
		if (!this.properties.canonicalizeOrganizations()) {
			return Set.of();
		}
		Map<String, Map<String, Integer>> variantsByKey = new HashMap<>();
		for (VCard vcard : vcards) {
			for (Organization organization : vcard.getOrganizations()) {
				String name = firstUnit(organization);
				String key = normalize(name);
				if (!key.isEmpty()) {
					variantsByKey.computeIfAbsent(key, (k) -> new HashMap<>()).merge(name.trim(), 1, Integer::sum);
				}
			}
		}

		Map<String, String> canonicalByKey = new HashMap<>();
		variantsByKey.forEach((key, variants) -> {
			if (variants.size() > 1) {
				canonicalByKey.put(key, electCanonical(variants));
			}
		});

		Set<VCard> changed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (VCard vcard : vcards) {
			for (Organization organization : vcard.getOrganizations()) {
				String name = firstUnit(organization);
				String canonical = canonicalByKey.get(normalize(name));
				if (canonical != null && !canonical.equals(name.trim())) {
					log.info("Canonicalizing organization '{}' -> '{}'", name.trim(), canonical);
					organization.getValues().set(0, canonical);
					changed.add(vcard);
				}
			}
		}
		return changed;
	}

	/** Most frequent variant wins; ties break to the longest, then lexicographic. */
	private String electCanonical(Map<String, Integer> variants) {
		return variants.entrySet()
			.stream()
			.max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
				.thenComparingInt((entry) -> entry.getKey().length())
				.thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
			.orElseThrow()
			.getKey();
	}

	private String firstUnit(Organization organization) {
		List<String> units = organization.getValues();
		return (!units.isEmpty() && units.get(0) != null) ? units.get(0) : "";
	}

	/** "Acme AG." / "acme GmbH" / "Acme, Inc." → "acme". */
	private String normalize(String name) {
		String lower = name.trim().toLowerCase(Locale.ROOT);
		String withoutSuffixes = LEGAL_SUFFIXES.matcher(lower).replaceAll(" ");
		return withoutSuffixes.replaceAll("[.,]", " ").replaceAll("\\s+", " ").trim();
	}

}
