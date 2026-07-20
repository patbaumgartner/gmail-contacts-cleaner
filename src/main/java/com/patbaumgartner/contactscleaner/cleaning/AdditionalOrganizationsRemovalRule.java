package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Iterator;
import java.util.List;

import ezvcard.VCard;
import ezvcard.property.Organization;

/**
 * Removes additional organizations beyond the primary one. Old LinkedIn/XING imports
 * wrote the person's <em>entire employment history</em> into the contact — Google shows
 * these as "Other Organizations" (and exports them to CSV as a custom field, while
 * CardDAV serves them as extra {@code ORG} properties). The current employer is the first
 * {@code ORG}; the rest is a stale CV.
 *
 * <p>
 * Destructive, therefore disabled by default; enable via
 * {@code contacts-cleaner.cleaning.remove-additional-organizations=true}. Ordered after
 * the defunct-organization and self-organization rules, so "first" means the first
 * organization that <em>survived</em> those.
 */
final class AdditionalOrganizationsRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		List<Organization> organizations = vcard.getOrganizations();
		if (organizations.size() < 2) {
			return false;
		}
		boolean first = true;
		for (Iterator<Organization> iterator = organizations.iterator(); iterator.hasNext();) {
			iterator.next();
			if (first) {
				first = false;
			}
			else {
				iterator.remove();
			}
		}
		return true;
	}

}
