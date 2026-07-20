package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;
import ezvcard.property.Impp;

/**
 * Removes instant-messenger handles ({@code IMPP}) — ICQ, AIM, Yahoo Messenger, MSN,
 * Jabber, Google Talk, Skype usernames from the 2000s. The networks are dead or the
 * handles unused; Google's own UI stopped showing IM fields in 2019 and only drags them
 * along as legacy data.
 *
 * <p>
 * Enabled by default; disable via
 * {@code contacts-cleaner.cleaning.remove-instant-messengers=false} if you still treasure
 * your ICQ numbers.
 */
final class InstantMessengerRemovalRule implements VCardCleaningRule {

	@Override
	public boolean apply(VCard vcard) {
		if (vcard.getImpps().isEmpty()) {
			return false;
		}
		vcard.removeProperties(Impp.class);
		return true;
	}

}
