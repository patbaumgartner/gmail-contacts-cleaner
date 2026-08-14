package com.patbaumgartner.contactscleaner.peopleapi;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Normalized e-mail addresses and phone numbers of the contacts that already exist in My
 * Contacts, used to decide which Other contacts are worth promoting.
 *
 * <p>
 * Normalization lives here rather than at the call site so that both sides of the
 * comparison — the caller's existing contacts and the Other contacts fetched from the
 * People API — can never drift apart and silently stop matching.
 *
 * @param emailAddresses trimmed, lower-cased e-mail addresses
 * @param phoneNumbers digits only, with a leading {@code 00} international-call prefix
 * stripped so {@code 0041…} and {@code +41…} compare equal
 */
public record ContactIdentifiers(Set<String> emailAddresses, Set<String> phoneNumbers) {

	/** No contacts known yet — every Other contact is a promotion candidate. */
	public static final ContactIdentifiers NONE = new ContactIdentifiers(Set.of(), Set.of());

	public ContactIdentifiers {
		emailAddresses = Set.copyOf(emailAddresses);
		phoneNumbers = Set.copyOf(phoneNumbers);
	}

	/**
	 * Normalizes and indexes the given raw values, ignoring blanks.
	 * @param emailAddresses raw e-mail addresses, may contain {@code null}
	 * @param phoneNumbers raw phone numbers, may contain {@code null}
	 * @return the normalized identifiers
	 */
	public static ContactIdentifiers of(Collection<String> emailAddresses, Collection<String> phoneNumbers) {
		return new ContactIdentifiers(normalizeAll(emailAddresses, ContactIdentifiers::normalizeEmail),
				normalizeAll(phoneNumbers, ContactIdentifiers::normalizePhone));
	}

	static String normalizeEmail(String value) {
		return (value != null) ? value.trim().toLowerCase(Locale.ROOT) : "";
	}

	static String normalizePhone(String value) {
		String digits = (value != null) ? value.replaceAll("\\D", "") : "";
		return digits.startsWith("00") ? digits.substring(2) : digits;
	}

	private static Set<String> normalizeAll(Collection<String> values, UnaryOperator<String> normalizer) {
		Set<String> normalized = new LinkedHashSet<>();
		for (String value : values) {
			String candidate = normalizer.apply(value);
			if (!candidate.isEmpty()) {
				normalized.add(candidate);
			}
		}
		return normalized;
	}
}
