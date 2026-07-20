package com.patbaumgartner.contactscleaner.cleaning;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Behavioral switches for the cleaning rules, bound from
 * {@code contacts-cleaner.cleaning}.
 *
 * <p>
 * Destructive options default to {@code false}: out of the box the cleaner only
 * normalizes data, it never deletes anything.
 *
 * @param normalizePhoneNumbers strip separators and convert the {@code 00}
 * international-call prefix to {@code +}
 * @param removeDuplicatePhoneNumbers drop repeated phone numbers within one contact
 * @param normalizeEmailAddresses lower-case and trim e-mail addresses
 * @param removeDuplicateEmailAddresses drop repeated e-mail addresses within one contact
 * @param trimNames trim whitespace around given/family/middle/formatted names
 * @param removeNotes delete the free-text notes field of every contact
 * (<strong>destructive</strong>, off by default)
 * @param deleteEmptyContacts delete contacts that have neither a phone number nor an
 * e-mail address (<strong>destructive</strong>, off by default)
 */
@ConfigurationProperties(prefix = "contacts-cleaner.cleaning")
public record CleaningProperties(@DefaultValue("true") boolean normalizePhoneNumbers,
		@DefaultValue("true") boolean removeDuplicatePhoneNumbers,
		@DefaultValue("true") boolean normalizeEmailAddresses,
		@DefaultValue("true") boolean removeDuplicateEmailAddresses, @DefaultValue("true") boolean trimNames,
		@DefaultValue("false") boolean removeNotes, @DefaultValue("false") boolean deleteEmptyContacts) {
}
