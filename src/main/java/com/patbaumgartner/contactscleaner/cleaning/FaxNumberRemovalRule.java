package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Iterator;

import ezvcard.VCard;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.Telephone;

/**
 * Removes fax numbers ({@code TEL;TYPE=FAX}, work and home alike). It is not 1995: nobody
 * is going to send a fax, and the entries only clutter the dialer. Google exports these
 * with the labels {@code Work Fax} / {@code Home Fax}, which map to the vCard {@code FAX}
 * telephone type.
 *
 * <p>
 * Destructive, therefore disabled by default; enable via
 * {@code contacts-cleaner.cleaning.remove-fax-numbers=true}.
 */
final class FaxNumberRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		for (Iterator<Telephone> iterator = vcard.getTelephoneNumbers().iterator(); iterator.hasNext();) {
			Telephone telephone = iterator.next();
			if (telephone.getTypes().contains(TelephoneType.FAX)) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

}
