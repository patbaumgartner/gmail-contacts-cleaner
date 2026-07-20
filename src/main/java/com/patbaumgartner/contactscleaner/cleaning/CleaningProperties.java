package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;

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
 * @param removeFaxNumbers drop fax numbers ({@code TEL;TYPE=FAX}, work and home) — relics
 * nobody will ever dial (<strong>destructive</strong>, off by default)
 * @param removeInvalidPhoneNumbers drop phone numbers that are provably invalid for their
 * country (wrong length, impossible prefix, undialable fragments) — judged via
 * libphonenumber; national numbers are only judged when {@link #phoneRegion()} is set
 * (<strong>destructive</strong>, off by default)
 * @param normalizeEmailAddresses lower-case and trim e-mail addresses
 * @param removeDuplicateEmailAddresses drop repeated e-mail addresses within one contact
 * @param removeInvalidEmails drop e-mail addresses that are not syntactically valid
 * (missing {@code @}, no domain, illegal characters) — import accidents that can never
 * receive mail
 * @param verifyEmailDomains resolve each mail domain via DNS and remove addresses whose
 * domain authoritatively no longer exists (NXDOMAIN); DNS timeouts never count as proof
 * (<strong>destructive</strong> and requires network access, off by default)
 * @param trimNames trim whitespace around given/family/middle/formatted names
 * @param normalizeLabels replace custom e-mail/address labels with the standard vCard
 * types: work/home variants (localized included) become {@code TYPE=WORK}/{@code HOME},
 * other custom labels ({@code Internet email}, {@code Obsolete}, ...) are dropped in
 * favor of the default type
 * @param removeRedundantAddresses drop postal addresses that are a less complete version
 * of another address on the same contact (all filled components equal, the survivor has
 * more) — different addresses are never touched
 * @param removeGeoCoordinateAddresses drop postal addresses consisting of nothing but a
 * latitude/longitude pair — check-in/geotagging debris, not a postal address
 * @param removeEmptyProperties drop properties whose value is entirely blank (empty
 * {@code TEL}/{@code EMAIL}/{@code URL}/{@code NOTE}, all-blank {@code ORG}/{@code ADR})
 * — classic sync debris
 * @param detectDuplicateContacts report-only detection of contacts that appear to be
 * duplicates of each other (shared phone/e-mail or near-identical name); nothing is
 * merged or deleted, candidates are logged in the run summary
 * @param repairFlippedNames swap given and family name when the contact's own e-mail
 * address proves they were entered in the wrong order (name {@code Muster Max} with
 * e-mail {@code max.muster@…}); without such evidence the name is never touched
 * @param extractBirthdays promote a keyword-tagged birthday found in the notes (e.g.
 * {@code "Geburtstag: 12.03.1980"}) to a proper {@code BDAY} property; never overwrites
 * an existing birthday, never modifies the note
 * @param removeSocialNetworkNotes strip machine-generated XING/LinkedIn sync content
 * (profile URLs, {@code "Created via LinkedIn"}, LinkedIn import
 * {@code Position:}/{@code Connected on} blocks) from notes; user-written text in the
 * same note is preserved
 * @param cleanUrls remove website URLs of dead/unwanted services (Klout, Google+,
 * Gravatar, XING, ...), trim and deduplicate the remaining ones
 * @param removeOrganizations organization names to delete (case-insensitive prefix match
 * with word boundary, so {@code Acme} also matches {@code Acme AG}) — for companies
 * that no longer exist; empty by default
 * @param removeCustomFields labels of custom fields to delete (case-insensitive, matched
 * against Apple-style {@code X-ABLabel} groups and {@code X-<label>} properties);
 * defaults to {@code Age} (a never-updated age is misinformation) and {@code Photo}
 * (stale avatar links). Empty list disables the rule
 * @param removeSelfOrganizations drop organizations that merely repeat the person's own
 * name ({@code FN: Jane Doe}, {@code ORG: Jane Doe}) — an import artifact
 * @param removeDanglingTitles drop job titles when the contact has no organization
 * (including titles orphaned by organization removal)
 * @param canonicalizeOrganizations cross-contact: rewrite organization spellings to the
 * most frequent variant per company ({@code acme AG} → {@code Acme AG}); folds
 * legal-form variants of the same brand — disable if you track subsidiaries
 * @param removeSharedPhoneNumbers remove phone numbers that appear on
 * {@link #sharedPhoneNumberThreshold()} or more contacts — those are company
 * switchboards, not direct lines (<strong>destructive</strong>, off by default)
 * @param sharedPhoneNumberThreshold minimum number of contacts sharing a phone number
 * before it is considered an office line (default {@code 3}, so a landline shared by a
 * couple survives)
 * @param removeNotes delete the free-text notes field of every contact
 * (<strong>destructive</strong>, off by default)
 * @param deleteEmptyContacts delete contacts that carry no information at all — no phone,
 * no e-mail, no birthday, no address, no URL, no note, no organization
 * (<strong>destructive</strong>, off by default)
 */
@ConfigurationProperties(prefix = "contacts-cleaner.cleaning")
public record CleaningProperties(@DefaultValue("true") boolean normalizePhoneNumbers,
		@DefaultValue("") String phoneRegion, @DefaultValue("true") boolean removeDuplicatePhoneNumbers,
		@DefaultValue("false") boolean removeFaxNumbers, @DefaultValue("false") boolean removeInvalidPhoneNumbers,
		@DefaultValue("true") boolean normalizeEmailAddresses,
		@DefaultValue("true") boolean removeDuplicateEmailAddresses, @DefaultValue("true") boolean removeInvalidEmails,
		@DefaultValue("false") boolean verifyEmailDomains, @DefaultValue("true") boolean trimNames,
		@DefaultValue("true") boolean normalizeLabels, @DefaultValue("true") boolean removeEmptyProperties,
		@DefaultValue("true") boolean removeRedundantAddresses,
		@DefaultValue("true") boolean removeGeoCoordinateAddresses,
		@DefaultValue("true") boolean detectDuplicateContacts, @DefaultValue("true") boolean repairFlippedNames,
		@DefaultValue("true") boolean extractBirthdays, @DefaultValue("true") boolean removeSocialNetworkNotes,
		@DefaultValue("true") boolean cleanUrls, @DefaultValue( {
				"Age", "Photo" }) List<String> removeCustomFields,
		@DefaultValue("") List<String> removeOrganizations, @DefaultValue("true") boolean removeSelfOrganizations,
		@DefaultValue("true") boolean removeDanglingTitles, @DefaultValue("true") boolean canonicalizeOrganizations,
		@DefaultValue("false") boolean removeSharedPhoneNumbers, @DefaultValue("2") int sharedPhoneNumberThreshold,
		@DefaultValue("false") boolean removeNotes, @DefaultValue("false") boolean deleteEmptyContacts){

	public CleaningProperties {
		removeCustomFields = (removeCustomFields != null) ? List.copyOf(removeCustomFields) : List.of();
		removeOrganizations = (removeOrganizations != null)
				? removeOrganizations.stream().filter((name) -> !name.isBlank()).toList() : List.of();
	}

	/**
	 * Returns conservative defaults, mainly for tests and programmatic use: all
	 * non-destructive rules on, no phone region, destructive rules off.
	 * @return default cleaning properties
	 */
	public static CleaningProperties defaults() {
		return new CleaningProperties(true, "", true, false, false, true, true, true, false, true, true, true, true,
				true, true, true, true, true, true, List.of("Age", "Photo"), List.of(), true, true, true, false, 2,
				false, false);
	}

	/**
	 * Returns a copy with the given phone region.
	 * @param phoneRegion the ISO 3166-1 alpha-2 region
	 * @return a new instance
	 */
	public CleaningProperties withPhoneRegion(String phoneRegion) {
		return new CleaningProperties(normalizePhoneNumbers, phoneRegion, removeDuplicatePhoneNumbers, removeFaxNumbers,
				removeInvalidPhoneNumbers, normalizeEmailAddresses, removeDuplicateEmailAddresses, removeInvalidEmails,
				verifyEmailDomains, trimNames, normalizeLabels, removeEmptyProperties, removeRedundantAddresses,
				removeGeoCoordinateAddresses, detectDuplicateContacts, repairFlippedNames, extractBirthdays,
				removeSocialNetworkNotes, cleanUrls, removeCustomFields, removeOrganizations, removeSelfOrganizations,
				removeDanglingTitles, canonicalizeOrganizations, removeSharedPhoneNumbers, sharedPhoneNumberThreshold,
				removeNotes, deleteEmptyContacts);
	}

	/**
	 * Returns a copy with the destructive options set as given.
	 * @param removeNotes delete notes
	 * @param deleteEmptyContacts delete empty contacts
	 * @return a new instance
	 */
	public CleaningProperties withDestructiveOptions(boolean removeNotes, boolean deleteEmptyContacts) {
		return new CleaningProperties(normalizePhoneNumbers, phoneRegion, removeDuplicatePhoneNumbers, removeFaxNumbers,
				removeInvalidPhoneNumbers, normalizeEmailAddresses, removeDuplicateEmailAddresses, removeInvalidEmails,
				verifyEmailDomains, trimNames, normalizeLabels, removeEmptyProperties, removeRedundantAddresses,
				removeGeoCoordinateAddresses, detectDuplicateContacts, repairFlippedNames, extractBirthdays,
				removeSocialNetworkNotes, cleanUrls, removeCustomFields, removeOrganizations, removeSelfOrganizations,
				removeDanglingTitles, canonicalizeOrganizations, removeSharedPhoneNumbers, sharedPhoneNumberThreshold,
				removeNotes, deleteEmptyContacts);
	}
}
