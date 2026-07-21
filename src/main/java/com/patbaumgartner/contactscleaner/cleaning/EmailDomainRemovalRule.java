package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.property.Email;

/**
 * Removes e-mail addresses whose domain is in a configured block list. Subdomains of a
 * configured domain are removed too, while lookalike domains are kept.
 */
final class EmailDomainRemovalRule implements VCardCleaningRule {

	private final Set<String> domains;

	EmailDomainRemovalRule(Set<String> domains) {
		this.domains = domains;
	}

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		for (Iterator<Email> iterator = vcard.getEmails().iterator(); iterator.hasNext();) {
			Email email = iterator.next();
			if (belongsToRemovedDomain(email.getValue())) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

	private boolean belongsToRemovedDomain(String email) {
		if (email == null) {
			return false;
		}
		int at = email.lastIndexOf('@');
		if (at < 0 || at == email.length() - 1) {
			return false;
		}
		String domain = email.substring(at + 1).trim().toLowerCase(Locale.ROOT);
		return domains.stream()
			.anyMatch((removedDomain) -> domain.equals(removedDomain) || domain.endsWith("." + removedDomain));
	}

}