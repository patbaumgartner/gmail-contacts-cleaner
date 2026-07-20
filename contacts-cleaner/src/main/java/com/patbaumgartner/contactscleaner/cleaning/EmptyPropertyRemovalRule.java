package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Iterator;
import java.util.List;

import ezvcard.VCard;
import ezvcard.property.Address;
import ezvcard.property.Email;
import ezvcard.property.Note;
import ezvcard.property.Organization;
import ezvcard.property.Telephone;
import ezvcard.property.Url;

/**
 * Removes properties whose value is entirely blank — classic debris left behind by years
 * of syncing across devices and import/export round trips: empty {@code TEL},
 * {@code EMAIL}, {@code URL} and {@code NOTE} lines, organizations whose units are all
 * blank ({@code ORG:;;}), and addresses with every component empty.
 *
 * <p>
 * This is considered non-destructive: a blank property carries no information.
 */
final class EmptyPropertyRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		changed |= removeBlankText(vcard.getTelephoneNumbers().iterator(), Telephone::getText);
		changed |= removeBlankText(vcard.getEmails().iterator(), Email::getValue);
		changed |= removeBlankText(vcard.getUrls().iterator(), Url::getValue);
		changed |= removeBlankText(vcard.getNotes().iterator(), Note::getValue);
		changed |= removeBlankOrganizations(vcard);
		changed |= removeBlankAddresses(vcard);
		return changed;
	}

	private <T> boolean removeBlankText(Iterator<T> iterator, java.util.function.Function<T, String> value) {
		boolean changed = false;
		while (iterator.hasNext()) {
			String text = value.apply(iterator.next());
			if (text == null || text.isBlank()) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

	private boolean removeBlankOrganizations(VCard vcard) {
		boolean changed = false;
		for (Iterator<Organization> iterator = vcard.getOrganizations().iterator(); iterator.hasNext();) {
			List<String> units = iterator.next().getValues();
			if (units.stream().allMatch((unit) -> unit == null || unit.isBlank())) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

	private boolean removeBlankAddresses(VCard vcard) {
		boolean changed = false;
		for (Iterator<Address> iterator = vcard.getAddresses().iterator(); iterator.hasNext();) {
			if (isBlank(iterator.next())) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

	private boolean isBlank(Address address) {
		return isBlank(address.getStreetAddress()) && isBlank(address.getExtendedAddress())
				&& isBlank(address.getPoBox()) && isBlank(address.getLocality()) && isBlank(address.getRegion())
				&& isBlank(address.getPostalCode()) && isBlank(address.getCountry());
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

}
