package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Title;

/**
 * Removes job titles that dangle without any organization — "Senior Consultant" at…
 * nowhere. Covers both import artifacts (title copied without the company) and titles
 * orphaned by {@link OrganizationRemovalRule} deleting a defunct employer. Ordered after
 * all organization rules so the decision is made on the final state.
 */
final class DanglingTitleRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		if (vcard.getOrganizations().isEmpty() && !vcard.getTitles().isEmpty()) {
			vcard.removeProperties(Title.class);
			return true;
		}
		return false;
	}

}
