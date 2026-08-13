package com.patbaumgartner.contactscleaner.cleaning;

import java.util.List;
import java.util.Locale;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Behavioral switches for the cleaning rules, bound from
 * {@code contacts-cleaner.cleaning}.
 *
 * <p>
 * Destructive options default to {@code false}: out of the box the cleaner only
 * normalizes data, it never deletes anything meaningful. (Removing properties whose value
 * is completely blank is considered non-destructive.)
 *
 * <p>
 * Because the record has one component per rule, construct it programmatically through
 * {@link #builder()} / {@link #toBuilder()} rather than the positional canonical
 * constructor — a misplaced boolean in a 38-argument call changes behavior silently.
 *
 * @param normalizePhoneNumbers strip separators and convert the {@code 00}
 * international-call prefix to {@code +}; with {@link #phoneRegion()} set, numbers are
 * additionally parsed and formatted to E.164
 * @param phoneRegion ISO 3166-1 alpha-2 region (e.g. {@code CH}, {@code DE}) used to
 * interpret national numbers like {@code 044 668 18 00}; empty disables E.164 formatting
 * and falls back to plain separator stripping
 * @param removeDuplicatePhoneNumbers drop repeated phone numbers within one contact
 * @param correctPhoneTypes verify the mobile/landline classification against the
 * numbering plan (libphonenumber): provably mobile numbers gain {@code TYPE=CELL},
 * provably fixed-line numbers lose a wrong one; ambiguous plans (e.g. US) are never
 * judged
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
 * @param removeJunkNameSuffixes drop parenthesized name suffixes like {@code (JIRA)} or
 * {@code (whatsapp)} — messenger/phone import junk; real suffixes ({@code Jr.},
 * {@code PMP}) are kept
 * @param repairNames fix structurally broken names: all-uppercase given/family components
 * get smart casing ({@code MCDONALD} → {@code McDonald}), existing inner capitals are
 * preserved, known prefixes are canonicalized ({@code Dr} → {@code Dr.}), and e-mail
 * addresses stuck in name fields are moved to the contact's e-mails; a trailing
 * {@code Name (name@example.com)} display-name suffix is simplified and fills missing
 * given/family fields when unambiguous
 * @param removeWrappingNameQuotes remove wrapping quote characters around name fields
 * @param repairCommaFormattedNames rewrite unambiguous display names in the form
 * {@code Last, First} to {@code First Last}
 * @param normalizeLabels replace custom e-mail/address labels with the standard vCard
 * types: work/home variants (localized included) become {@code TYPE=WORK}/{@code HOME},
 * other custom labels ({@code Internet email}, {@code Obsolete}, ...) are dropped in
 * favor of the default type
 * @param removeEmptyProperties drop properties that carry no usable value: empty
 * {@code TEL}/{@code EMAIL}/{@code URL}/{@code NOTE}, all-blank {@code ORG}, blank or
 * duplicate {@code X-} properties, and addresses without any of PO box, street, extended
 * address, region or postal code — a bare {@code Zurich} / {@code Switzerland} pair is
 * not a postal address
 * @param removeRedundantAddresses drop postal addresses that are a less complete version
 * of another address on the same contact (all filled components equal, the survivor has
 * more) — different addresses are never touched
 * @param removeGeoCoordinateAddresses drop postal addresses consisting of nothing but a
 * latitude/longitude pair — check-in/geotagging debris, not a postal address
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
 * @param removeInstantMessengers drop instant-messenger handles ({@code IMPP}: ICQ, AIM,
 * Yahoo, Skype, ...) — dead networks Google's UI no longer shows
 * @param removeCustomFields labels of custom fields to delete (case-insensitive, matched
 * against Apple-style {@code X-ABLabel} groups and {@code X-<label>} properties);
 * defaults to {@code Age} (a never-updated age is misinformation) and {@code Photo}
 * (stale avatar links). Empty list disables the rule. <strong>Note:</strong> Google does
 * not expose custom fields over CardDAV — this rule only reaches label debris that
 * appears in the vCards; fields like a CSV-exported {@code Age} are invisible to the
 * protocol and must be cleaned once via CSV export/import
 * @param removeOrganizations organization names to delete (case-insensitive prefix match
 * with word boundary, so {@code Acme} also matches {@code Acme AG}) — for companies that
 * no longer exist; empty by default
 * @param removeAdditionalOrganizations keep only the primary organization and drop the
 * rest — old LinkedIn/XING imports stored the whole employment history as extra
 * {@code ORG} entries (Google shows them as "Other Organizations")
 * (<strong>destructive</strong>, off by default)
 * @param removeSelfOrganizations drop organizations that merely repeat the person's own
 * name ({@code FN: Jane Doe}, {@code ORG: Jane Doe}) — an import artifact
 * @param removeDanglingTitles drop job titles when the contact has no organization
 * (including titles orphaned by organization removal)
 * @param canonicalizeOrganizations cross-contact: rewrite organization spellings to the
 * most frequent variant per company ({@code acme AG} → {@code Acme AG}); folds legal-form
 * variants of the same brand — disable if you track subsidiaries
 * @param removeSharedPhoneNumbers remove phone numbers that appear on
 * {@link #sharedPhoneNumberThreshold()} or more contacts — those are company
 * switchboards, not direct lines (<strong>destructive</strong>, off by default)
 * @param sharedPhoneNumberThreshold minimum number of contacts sharing a phone number
 * before it is considered an office line (default {@code 2}; raise to {@code 3} to keep a
 * landline shared by a couple). Values below {@code 2} would delete every phone number
 * and are rejected
 * @param removeNotes delete the free-text notes field of every contact
 * (<strong>destructive</strong>, off by default)
 * @param deleteEmptyContacts delete contacts that carry no information at all — no phone,
 * no e-mail, no birthday, no address, no URL, no note, no organization
 * (<strong>destructive</strong>, off by default)
 * @param deleteBirthdayOnlyContacts delete contacts that have a birthday but no other
 * contact data beyond their name (<strong>destructive</strong>, off by default)
 * @param inferNamesFromEmailAddresses populate missing given/family names from an
 * unambiguous {@code first.last} or {@code first_last} e-mail local part
 * @param removeEmailDomains e-mail domains whose addresses should be deleted, including
 * subdomains; empty disables the rule
 */
@Validated
@ConfigurationProperties(prefix = "contacts-cleaner.cleaning")
public record CleaningProperties(@DefaultValue("true") boolean normalizePhoneNumbers,
		@DefaultValue("") String phoneRegion, @DefaultValue("true") boolean removeDuplicatePhoneNumbers,
		@DefaultValue("true") boolean correctPhoneTypes, @DefaultValue("false") boolean removeFaxNumbers,
		@DefaultValue("false") boolean removeInvalidPhoneNumbers, @DefaultValue("true") boolean normalizeEmailAddresses,
		@DefaultValue("true") boolean removeDuplicateEmailAddresses, @DefaultValue("true") boolean removeInvalidEmails,
		@DefaultValue("false") boolean verifyEmailDomains, @DefaultValue("true") boolean trimNames,
		@DefaultValue("true") boolean removeJunkNameSuffixes, @DefaultValue("true") boolean repairNames,
		@DefaultValue("true") boolean removeWrappingNameQuotes, @DefaultValue("true") boolean repairCommaFormattedNames,
		@DefaultValue("true") boolean normalizeLabels, @DefaultValue("true") boolean removeEmptyProperties,
		@DefaultValue("true") boolean removeRedundantAddresses,
		@DefaultValue("true") boolean removeGeoCoordinateAddresses,
		@DefaultValue("true") boolean detectDuplicateContacts, @DefaultValue("true") boolean repairFlippedNames,
		@DefaultValue("true") boolean extractBirthdays, @DefaultValue("true") boolean removeSocialNetworkNotes,
		@DefaultValue("true") boolean cleanUrls, @DefaultValue("true") boolean removeInstantMessengers, @DefaultValue( {
				"Age", "Photo" }) List<String> removeCustomFields,
		@DefaultValue("") List<String> removeOrganizations,
		@DefaultValue("false") boolean removeAdditionalOrganizations,
		@DefaultValue("true") boolean removeSelfOrganizations, @DefaultValue("true") boolean removeDanglingTitles,
		@DefaultValue("true") boolean canonicalizeOrganizations,
		@DefaultValue("false") boolean removeSharedPhoneNumbers,
		@DefaultValue("2") @Min(2) int sharedPhoneNumberThreshold, @DefaultValue("false") boolean removeNotes,
		@DefaultValue("false") boolean deleteEmptyContacts, @DefaultValue("false") boolean deleteBirthdayOnlyContacts,
		@DefaultValue("true") boolean inferNamesFromEmailAddresses, @DefaultValue("") List<String> removeEmailDomains){

	@ConstructorBinding
	public CleaningProperties {
		removeCustomFields = (removeCustomFields != null) ? List.copyOf(removeCustomFields) : List.of();
		removeOrganizations = (removeOrganizations != null)
				? removeOrganizations.stream().filter((name) -> !name.isBlank()).toList() : List.of();
		removeEmailDomains = (removeEmailDomains != null) ? removeEmailDomains.stream()
			.map(String::trim)
			.filter((domain) -> !domain.isEmpty())
			.map((domain) -> domain.toLowerCase(Locale.ROOT))
			.distinct()
			.toList() : List.of();
	}

	/**
	 * Returns the conservative defaults, mainly for tests and programmatic use: all
	 * non-destructive rules on, no phone region, destructive rules off.
	 * @return default cleaning properties
	 */
	public static CleaningProperties defaults() {
		return builder().build();
	}

	/**
	 * Returns a builder pre-filled with the defaults of {@link #defaults()}.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns a builder pre-filled with this instance's values.
	 * @return a new builder
	 */
	public Builder toBuilder() {
		return new Builder(this);
	}

	/**
	 * Named, order-independent construction of {@link CleaningProperties}. Every setter
	 * corresponds to one record component and defaults to the value the Spring binder
	 * would apply.
	 */
	public static final class Builder {

		private boolean normalizePhoneNumbers = true;

		private String phoneRegion = "";

		private boolean removeDuplicatePhoneNumbers = true;

		private boolean correctPhoneTypes = true;

		private boolean removeFaxNumbers;

		private boolean removeInvalidPhoneNumbers;

		private boolean normalizeEmailAddresses = true;

		private boolean removeDuplicateEmailAddresses = true;

		private boolean removeInvalidEmails = true;

		private boolean verifyEmailDomains;

		private boolean trimNames = true;

		private boolean removeJunkNameSuffixes = true;

		private boolean repairNames = true;

		private boolean removeWrappingNameQuotes = true;

		private boolean repairCommaFormattedNames = true;

		private boolean normalizeLabels = true;

		private boolean removeEmptyProperties = true;

		private boolean removeRedundantAddresses = true;

		private boolean removeGeoCoordinateAddresses = true;

		private boolean detectDuplicateContacts = true;

		private boolean repairFlippedNames = true;

		private boolean extractBirthdays = true;

		private boolean removeSocialNetworkNotes = true;

		private boolean cleanUrls = true;

		private boolean removeInstantMessengers = true;

		private List<String> removeCustomFields = List.of("Age", "Photo");

		private List<String> removeOrganizations = List.of();

		private boolean removeAdditionalOrganizations;

		private boolean removeSelfOrganizations = true;

		private boolean removeDanglingTitles = true;

		private boolean canonicalizeOrganizations = true;

		private boolean removeSharedPhoneNumbers;

		private int sharedPhoneNumberThreshold = 2;

		private boolean removeNotes;

		private boolean deleteEmptyContacts;

		private boolean deleteBirthdayOnlyContacts;

		private boolean inferNamesFromEmailAddresses = true;

		private List<String> removeEmailDomains = List.of();

		private Builder() {
		}

		private Builder(CleaningProperties source) {
			this.normalizePhoneNumbers = source.normalizePhoneNumbers();
			this.phoneRegion = source.phoneRegion();
			this.removeDuplicatePhoneNumbers = source.removeDuplicatePhoneNumbers();
			this.correctPhoneTypes = source.correctPhoneTypes();
			this.removeFaxNumbers = source.removeFaxNumbers();
			this.removeInvalidPhoneNumbers = source.removeInvalidPhoneNumbers();
			this.normalizeEmailAddresses = source.normalizeEmailAddresses();
			this.removeDuplicateEmailAddresses = source.removeDuplicateEmailAddresses();
			this.removeInvalidEmails = source.removeInvalidEmails();
			this.verifyEmailDomains = source.verifyEmailDomains();
			this.trimNames = source.trimNames();
			this.removeJunkNameSuffixes = source.removeJunkNameSuffixes();
			this.repairNames = source.repairNames();
			this.removeWrappingNameQuotes = source.removeWrappingNameQuotes();
			this.repairCommaFormattedNames = source.repairCommaFormattedNames();
			this.normalizeLabels = source.normalizeLabels();
			this.removeEmptyProperties = source.removeEmptyProperties();
			this.removeRedundantAddresses = source.removeRedundantAddresses();
			this.removeGeoCoordinateAddresses = source.removeGeoCoordinateAddresses();
			this.detectDuplicateContacts = source.detectDuplicateContacts();
			this.repairFlippedNames = source.repairFlippedNames();
			this.extractBirthdays = source.extractBirthdays();
			this.removeSocialNetworkNotes = source.removeSocialNetworkNotes();
			this.cleanUrls = source.cleanUrls();
			this.removeInstantMessengers = source.removeInstantMessengers();
			this.removeCustomFields = source.removeCustomFields();
			this.removeOrganizations = source.removeOrganizations();
			this.removeAdditionalOrganizations = source.removeAdditionalOrganizations();
			this.removeSelfOrganizations = source.removeSelfOrganizations();
			this.removeDanglingTitles = source.removeDanglingTitles();
			this.canonicalizeOrganizations = source.canonicalizeOrganizations();
			this.removeSharedPhoneNumbers = source.removeSharedPhoneNumbers();
			this.sharedPhoneNumberThreshold = source.sharedPhoneNumberThreshold();
			this.removeNotes = source.removeNotes();
			this.deleteEmptyContacts = source.deleteEmptyContacts();
			this.deleteBirthdayOnlyContacts = source.deleteBirthdayOnlyContacts();
			this.inferNamesFromEmailAddresses = source.inferNamesFromEmailAddresses();
			this.removeEmailDomains = source.removeEmailDomains();
		}

		public Builder normalizePhoneNumbers(boolean value) {
			this.normalizePhoneNumbers = value;
			return this;
		}

		public Builder phoneRegion(String value) {
			this.phoneRegion = value;
			return this;
		}

		public Builder removeDuplicatePhoneNumbers(boolean value) {
			this.removeDuplicatePhoneNumbers = value;
			return this;
		}

		public Builder correctPhoneTypes(boolean value) {
			this.correctPhoneTypes = value;
			return this;
		}

		public Builder removeFaxNumbers(boolean value) {
			this.removeFaxNumbers = value;
			return this;
		}

		public Builder removeInvalidPhoneNumbers(boolean value) {
			this.removeInvalidPhoneNumbers = value;
			return this;
		}

		public Builder normalizeEmailAddresses(boolean value) {
			this.normalizeEmailAddresses = value;
			return this;
		}

		public Builder removeDuplicateEmailAddresses(boolean value) {
			this.removeDuplicateEmailAddresses = value;
			return this;
		}

		public Builder removeInvalidEmails(boolean value) {
			this.removeInvalidEmails = value;
			return this;
		}

		public Builder verifyEmailDomains(boolean value) {
			this.verifyEmailDomains = value;
			return this;
		}

		public Builder trimNames(boolean value) {
			this.trimNames = value;
			return this;
		}

		public Builder removeJunkNameSuffixes(boolean value) {
			this.removeJunkNameSuffixes = value;
			return this;
		}

		public Builder repairNames(boolean value) {
			this.repairNames = value;
			return this;
		}

		public Builder removeWrappingNameQuotes(boolean value) {
			this.removeWrappingNameQuotes = value;
			return this;
		}

		public Builder repairCommaFormattedNames(boolean value) {
			this.repairCommaFormattedNames = value;
			return this;
		}

		public Builder normalizeLabels(boolean value) {
			this.normalizeLabels = value;
			return this;
		}

		public Builder removeEmptyProperties(boolean value) {
			this.removeEmptyProperties = value;
			return this;
		}

		public Builder removeRedundantAddresses(boolean value) {
			this.removeRedundantAddresses = value;
			return this;
		}

		public Builder removeGeoCoordinateAddresses(boolean value) {
			this.removeGeoCoordinateAddresses = value;
			return this;
		}

		public Builder detectDuplicateContacts(boolean value) {
			this.detectDuplicateContacts = value;
			return this;
		}

		public Builder repairFlippedNames(boolean value) {
			this.repairFlippedNames = value;
			return this;
		}

		public Builder extractBirthdays(boolean value) {
			this.extractBirthdays = value;
			return this;
		}

		public Builder removeSocialNetworkNotes(boolean value) {
			this.removeSocialNetworkNotes = value;
			return this;
		}

		public Builder cleanUrls(boolean value) {
			this.cleanUrls = value;
			return this;
		}

		public Builder removeInstantMessengers(boolean value) {
			this.removeInstantMessengers = value;
			return this;
		}

		public Builder removeCustomFields(List<String> value) {
			this.removeCustomFields = value;
			return this;
		}

		public Builder removeOrganizations(List<String> value) {
			this.removeOrganizations = value;
			return this;
		}

		public Builder removeAdditionalOrganizations(boolean value) {
			this.removeAdditionalOrganizations = value;
			return this;
		}

		public Builder removeSelfOrganizations(boolean value) {
			this.removeSelfOrganizations = value;
			return this;
		}

		public Builder removeDanglingTitles(boolean value) {
			this.removeDanglingTitles = value;
			return this;
		}

		public Builder canonicalizeOrganizations(boolean value) {
			this.canonicalizeOrganizations = value;
			return this;
		}

		public Builder removeSharedPhoneNumbers(boolean value) {
			this.removeSharedPhoneNumbers = value;
			return this;
		}

		public Builder sharedPhoneNumberThreshold(int value) {
			this.sharedPhoneNumberThreshold = value;
			return this;
		}

		public Builder removeNotes(boolean value) {
			this.removeNotes = value;
			return this;
		}

		public Builder deleteEmptyContacts(boolean value) {
			this.deleteEmptyContacts = value;
			return this;
		}

		public Builder deleteBirthdayOnlyContacts(boolean value) {
			this.deleteBirthdayOnlyContacts = value;
			return this;
		}

		public Builder inferNamesFromEmailAddresses(boolean value) {
			this.inferNamesFromEmailAddresses = value;
			return this;
		}

		public Builder removeEmailDomains(List<String> value) {
			this.removeEmailDomains = value;
			return this;
		}

		/**
		 * Builds the immutable properties instance.
		 * @return the configured cleaning properties
		 */
		public CleaningProperties build() {
			return new CleaningProperties(this.normalizePhoneNumbers, this.phoneRegion,
					this.removeDuplicatePhoneNumbers, this.correctPhoneTypes, this.removeFaxNumbers,
					this.removeInvalidPhoneNumbers, this.normalizeEmailAddresses, this.removeDuplicateEmailAddresses,
					this.removeInvalidEmails, this.verifyEmailDomains, this.trimNames, this.removeJunkNameSuffixes,
					this.repairNames, this.removeWrappingNameQuotes, this.repairCommaFormattedNames,
					this.normalizeLabels, this.removeEmptyProperties, this.removeRedundantAddresses,
					this.removeGeoCoordinateAddresses, this.detectDuplicateContacts, this.repairFlippedNames,
					this.extractBirthdays, this.removeSocialNetworkNotes, this.cleanUrls, this.removeInstantMessengers,
					this.removeCustomFields, this.removeOrganizations, this.removeAdditionalOrganizations,
					this.removeSelfOrganizations, this.removeDanglingTitles, this.canonicalizeOrganizations,
					this.removeSharedPhoneNumbers, this.sharedPhoneNumberThreshold, this.removeNotes,
					this.deleteEmptyContacts, this.deleteBirthdayOnlyContacts, this.inferNamesFromEmailAddresses,
					this.removeEmailDomains);
		}

	}
}
