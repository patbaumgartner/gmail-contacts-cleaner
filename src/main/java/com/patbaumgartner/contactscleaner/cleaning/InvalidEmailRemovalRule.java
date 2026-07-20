package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Iterator;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.Email;

/**
 * Removes e-mail addresses that are not syntactically valid — typically import accidents
 * like a phone number or a bare name landing in the e-mail field, or a truncated address
 * missing its domain. Such values can never receive mail, so removing them is considered
 * non-destructive.
 *
 * <p>
 * The check is a pragmatic subset of RFC 5322 (the full grammar accepts addresses no mail
 * provider would): exactly one {@code @}, a non-empty local part without spaces, and a
 * domain with at least one dot and a two-letter-plus TLD.
 */
final class InvalidEmailRemovalRule implements VCardCleaningRule {

	/**
	 * Pragmatic subset of RFC 5322: one {@code @}, non-blank local part without
	 * whitespace, dotted domain labels, two-letter-plus TLD. Possessive quantifiers
	 * ({@code ++}) keep the match linear — no backtracking, no ReDoS.
	 */
	private static final Pattern VALID_EMAIL = Pattern
		.compile("^[A-Za-z0-9!#$%&'*+/=?^_`{|}~.-]++@(?:[A-Za-z0-9-]++\\.)++[A-Za-z]{2,}$");

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		for (Iterator<Email> iterator = vcard.getEmails().iterator(); iterator.hasNext();) {
			String value = iterator.next().getValue();
			if (value != null && !value.isBlank() && !VALID_EMAIL.matcher(value.trim()).matches()) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

}
