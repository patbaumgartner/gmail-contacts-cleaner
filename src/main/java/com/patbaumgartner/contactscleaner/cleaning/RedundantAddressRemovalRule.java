package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

import ezvcard.VCard;
import ezvcard.property.Address;

/**
 * Removes redundant postal addresses: when one address is a less complete version of
 * another (every filled component matches, the other has more), only the richer one
 * survives. Classic sync artifact — one device stored street + city, another the full
 * address with postal code and country, and merging kept both.
 *
 * <p>
 * Two addresses count as redundant only when all components that are present on
 * <em>both</em> are equal (case-insensitive, trimmed) and one side has no component the
 * other lacks a match for. Different addresses (home vs. office) are never touched. Exact
 * duplicates keep the first occurrence.
 */
final class RedundantAddressRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		List<Address> addresses = vcard.getAddresses();
		if (addresses.size() < 2) {
			return false;
		}
		boolean changed = false;
		for (int i = 0; i < addresses.size(); i++) {
			for (int j = addresses.size() - 1; j > i; j--) {
				Address richer = addresses.get(i);
				Address poorer = addresses.get(j);
				if (isContainedIn(poorer, richer)) {
					addresses.remove(j);
					changed = true;
				}
				else if (isContainedIn(richer, poorer)) {
					// The later address is the richer one — replace the earlier.
					addresses.set(i, poorer);
					addresses.remove(j);
					changed = true;
				}
			}
		}
		return changed;
	}

	/**
	 * Every non-blank component of {@code candidate} equals its counterpart in
	 * {@code other}.
	 */
	private boolean isContainedIn(Address candidate, Address other) {
		return componentMatches(candidate.getStreetAddress(), other.getStreetAddress())
				&& componentMatches(candidate.getExtendedAddress(), other.getExtendedAddress())
				&& componentMatches(candidate.getPoBox(), other.getPoBox())
				&& componentMatches(candidate.getLocality(), other.getLocality())
				&& componentMatches(candidate.getRegion(), other.getRegion())
				&& componentMatches(candidate.getPostalCode(), other.getPostalCode())
				&& componentMatches(candidate.getCountry(), other.getCountry());
	}

	private boolean componentMatches(String candidate, String other) {
		if (candidate == null || candidate.isBlank()) {
			return true;
		}
		return Objects.equals(normalize(candidate), normalize(other));
	}

	private String normalize(String value) {
		return (value != null) ? value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ") : null;
	}

}
