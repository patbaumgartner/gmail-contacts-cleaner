package com.patbaumgartner.contactscleaner.cleaning;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Behavioral switches for the cleaning rules, bound from
 * {@code contacts-cleaner.cleaning}.
 *
 * <p>
 * Destructive options default to {@code false}: out of the box the cleaner only
 * normalizes data, it never deletes anything meaningful. (Removing properties whose value
 * is completely blank is considered non-destructive.)
 *
 * @param normalizePhoneNumbers strip separators and convert the {@code 00}
 * international-call prefix to {@code +}; with {@link #phoneRegion()} set, numbers are
 * additionally parsed and formatted to E.164
 * @param phoneRegion ISO 3166-1 alpha-2 region (e.g. {@code CH}, {@code DE}) used to
 * interpret national numbers like {@code 044 668 18 00}; empty disables E.164 formatting
 * and falls back to plain separator stripping
 * @param removeDuplicatePhoneNumbers drop repeated phone numbers within one contact
 * @param normalizeEmailAddresses lower-case and trim e-mail addresses
 * @param removeDuplicateEmailAddresses drop repeated e-mail addresses within one contact
 * @param trimNames trim whitespace around given/family/middle/formatted names
 * @param removeEmptyProperties drop properties whose value is entirely blank (empty
 * {@code TEL}/{@code EMAIL}/{@code URL}/{@code NOTE}, all-blank {@code ORG}/{@code ADR})
 * — classic sync debris
 * @param detectDuplicateContacts report-only detection of contacts that appear to be
 * duplicates of each other (shared phone/e-mail or near-identical name); nothing is
 * merged or deleted, candidates are logged in the run summary
 * @param extractBirthdays promote a keyword-tagged birthday found in the notes (e.g.
 * {@code "Geburtstag: 12.03.1980"}) to a proper {@code BDAY} property; never overwrites
 * an existing birthday, never modifies the note
 * @param removeSocialNetworkNotes strip machine-generated XING/LinkedIn sync lines
 * (profile URLs, {@code "Created via LinkedIn"}) from notes; user-written text in the
 * same note is preserved
 * @param removeNotes delete the free-text notes field of every contact
 * (<strong>destructive</strong>, off by default)
 * @param deleteEmptyContacts delete contacts that have neither a phone number nor an
 * e-mail address (<strong>destructive</strong>, off by default)
 */
@ConfigurationProperties(prefix = "contacts-cleaner.cleaning")
public record CleaningProperties(@DefaultValue("true") boolean normalizePhoneNumbers,
		@DefaultValue("") String phoneRegion, @DefaultValue("true") boolean removeDuplicatePhoneNumbers,
		@DefaultValue("true") boolean normalizeEmailAddresses,
		@DefaultValue("true") boolean removeDuplicateEmailAddresses, @DefaultValue("true") boolean trimNames,
		@DefaultValue("true") boolean removeEmptyProperties, @DefaultValue("true") boolean detectDuplicateContacts,
		@DefaultValue("true") boolean extractBirthdays, @DefaultValue("true") boolean removeSocialNetworkNotes,
		@DefaultValue("false") boolean removeNotes, @DefaultValue("false") boolean deleteEmptyContacts) {

	/**
	 * Returns conservative defaults, mainly for tests and programmatic use: all
	 * non-destructive rules on, no phone region, destructive rules off.
	 * @return default cleaning properties
	 */
	public static CleaningProperties defaults() {
		return new CleaningProperties(true, "", true, true, true, true, true, true, true, true, false, false);
	}

	/**
	 * Returns a copy with the given phone region.
	 * @param phoneRegion the ISO 3166-1 alpha-2 region
	 * @return a new instance
	 */
	public CleaningProperties withPhoneRegion(String phoneRegion) {
		return new CleaningProperties(normalizePhoneNumbers, phoneRegion, removeDuplicatePhoneNumbers,
				normalizeEmailAddresses, removeDuplicateEmailAddresses, trimNames, removeEmptyProperties,
				detectDuplicateContacts, extractBirthdays, removeSocialNetworkNotes, removeNotes, deleteEmptyContacts);
	}

	/**
	 * Returns a copy with the destructive options set as given.
	 * @param removeNotes delete notes
	 * @param deleteEmptyContacts delete empty contacts
	 * @return a new instance
	 */
	public CleaningProperties withDestructiveOptions(boolean removeNotes, boolean deleteEmptyContacts) {
		return new CleaningProperties(normalizePhoneNumbers, phoneRegion, removeDuplicatePhoneNumbers,
				normalizeEmailAddresses, removeDuplicateEmailAddresses, trimNames, removeEmptyProperties,
				detectDuplicateContacts, extractBirthdays, removeSocialNetworkNotes, removeNotes, deleteEmptyContacts);
	}
}
