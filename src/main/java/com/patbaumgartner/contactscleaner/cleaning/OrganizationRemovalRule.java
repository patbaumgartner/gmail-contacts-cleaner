package com.patbaumgartner.contactscleaner.cleaning;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.property.Organization;

/**
 * Removes organizations that no longer exist, by configured name — e.g. a company that
 * was dissolved or absorbed years ago and now only spreads stale information through the
 * address book.
 *
 * <p>
 * Matching is case-insensitive on the organization's name prefix, so a configured
 * {@code Namics} also matches {@code Namics AG} and {@code Namics (a Merkle
 * company)}, but not {@code Namicsson Consulting} (the prefix must end at a word
 * boundary). Configure via {@code contacts-cleaner.cleaning.remove-organizations}
 * (default: empty — nothing is removed until you name names).
 */
final class OrganizationRemovalRule implements VCardCleaningRule {

	private final Set<String> organizationNames;

	OrganizationRemovalRule(List<String> organizationNames) {
		Set<String> normalized = new HashSet<>();
		for (String name : organizationNames) {
			if (name != null && !name.isBlank()) {
				normalized.add(name.trim().toLowerCase(Locale.ROOT));
			}
		}
		this.organizationNames = Set.copyOf(normalized);
	}

	@Override
	public boolean apply(VCard vcard) {
		if (this.organizationNames.isEmpty()) {
			return false;
		}
		boolean changed = false;
		for (Iterator<Organization> iterator = vcard.getOrganizations().iterator(); iterator.hasNext();) {
			if (matches(iterator.next())) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

	private boolean matches(Organization organization) {
		for (String unit : organization.getValues()) {
			if (unit == null) {
				continue;
			}
			String normalized = unit.trim().toLowerCase(Locale.ROOT);
			for (String name : this.organizationNames) {
				if (normalized.equals(name) || (normalized.startsWith(name)
						&& !Character.isLetterOrDigit(normalized.charAt(name.length())))) {
					return true;
				}
			}
		}
		return false;
	}

}
