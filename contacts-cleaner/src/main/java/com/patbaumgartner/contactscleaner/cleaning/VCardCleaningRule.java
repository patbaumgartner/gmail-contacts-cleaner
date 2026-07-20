package com.patbaumgartner.contactscleaner.cleaning;

import ezvcard.VCard;

/**
 * A single, side-effect free cleanup rule applied to one {@link VCard}.
 *
 * <p>
 * Implementations mutate the passed vCard in place and report whether anything changed,
 * so callers can avoid writing unchanged contacts back to the server.
 */
interface VCardCleaningRule {

	/**
	 * Applies this rule to the given vCard.
	 * @param vcard the vCard to clean, mutated in place
	 * @return {@code true} if the vCard was modified
	 */
	boolean apply(VCard vcard);

}
