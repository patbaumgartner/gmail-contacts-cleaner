package com.patbaumgartner.contactscleaner.cleaning;

import java.util.ArrayList;
import java.util.List;

import ezvcard.VCard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Public API of the {@code cleaning} module: applies the configured
 * {@link VCardCleaningRule}s to a single vCard.
 *
 * <p>
 * Rule order matters — normalization runs before deduplication so that superficially
 * different representations of the same value collapse into one.
 */
@Component
public class ContactCleaner {

	private static final Logger log = LoggerFactory.getLogger(ContactCleaner.class);

	private final List<VCardCleaningRule> rules;

	private final CleaningProperties properties;

	ContactCleaner(CleaningProperties properties) {
		this.properties = properties;
		this.rules = buildRules(properties);
	}

	private static List<VCardCleaningRule> buildRules(CleaningProperties properties) {
		List<VCardCleaningRule> rules = new ArrayList<>();
		if (properties.trimNames()) {
			rules.add(new NameTrimmingRule());
		}
		if (properties.normalizePhoneNumbers()) {
			rules.add(new PhoneNumberNormalizationRule());
		}
		if (properties.removeDuplicatePhoneNumbers()) {
			rules.add(new DuplicatePhoneNumberRemovalRule());
		}
		if (properties.normalizeEmailAddresses()) {
			rules.add(new EmailNormalizationRule());
		}
		if (properties.removeDuplicateEmailAddresses()) {
			rules.add(new DuplicateEmailRemovalRule());
		}
		if (properties.removeNotes()) {
			rules.add(new NoteRemovalRule());
		}
		return List.copyOf(rules);
	}

	/**
	 * Cleans the given vCard in place.
	 * @param vcard the vCard to clean
	 * @return whether the vCard changed and whether it is empty (deletion candidate)
	 */
	public CleaningResult clean(VCard vcard) {
		boolean changed = false;
		for (VCardCleaningRule rule : rules) {
			changed |= rule.apply(vcard);
		}
		boolean empty = properties.deleteEmptyContacts() && isEmpty(vcard);
		if (log.isTraceEnabled()) {
			log.trace("Cleaned contact '{}': changed={}, empty={}", displayName(vcard), changed, empty);
		}
		return new CleaningResult(changed, empty);
	}

	/**
	 * A contact is considered empty when it has neither phone numbers nor e-mail
	 * addresses — mirroring the heuristic of the original 2011 gcontacts-cleaner.
	 */
	private boolean isEmpty(VCard vcard) {
		return vcard.getTelephoneNumbers().isEmpty() && vcard.getEmails().isEmpty();
	}

	private String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null) ? vcard.getFormattedName().getValue() : "<unnamed>";
	}

}
