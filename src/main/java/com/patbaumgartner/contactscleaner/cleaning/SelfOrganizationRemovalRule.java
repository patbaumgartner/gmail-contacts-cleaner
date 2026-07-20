package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Iterator;
import java.util.Locale;

import ezvcard.VCard;
import ezvcard.property.Organization;

/**
 * Removes organizations that merely repeat the person's own name ({@code FN: Jane Doe}
 * with {@code ORG: Jane Doe}) — a classic import artifact where a sync tool copied the
 * display name into the company field. Carries zero information and makes every such
 * contact look self-employed.
 */
final class SelfOrganizationRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		String name = displayName(vcard);
		if (name.isEmpty()) {
			return false;
		}
		boolean changed = false;
		for (Iterator<Organization> iterator = vcard.getOrganizations().iterator(); iterator.hasNext();) {
			Organization organization = iterator.next();
			if (!organization.getValues().isEmpty() && organization.getValues().get(0) != null
					&& normalize(organization.getValues().get(0)).equals(name)) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

	private String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null && vcard.getFormattedName().getValue() != null)
				? normalize(vcard.getFormattedName().getValue()) : "";
	}

	private String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

}
