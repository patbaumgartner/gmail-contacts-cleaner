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

	public ContactCleaner(CleaningProperties properties) {
		this.properties = properties;
		this.rules = buildRules(properties);
	}

	private static List<VCardCleaningRule> buildRules(CleaningProperties properties) {
		List<VCardCleaningRule> rules = new ArrayList<>();
		if (properties.removeEmptyProperties()) {
			rules.add(new EmptyPropertyRemovalRule());
		}
		if (properties.removeGeoCoordinateAddresses()) {
			rules.add(new GeoCoordinateAddressRemovalRule());
		}
		if (properties.removeRedundantAddresses()) {
			rules.add(new RedundantAddressRemovalRule());
		}
		if (properties.trimNames()) {
			rules.add(new NameTrimmingRule());
		}
		if (properties.removeJunkNameSuffixes()) {
			rules.add(new JunkNameSuffixRemovalRule());
		}
		if (properties.repairNames()) {
			rules.add(new NameRepairRule());
		}
		if (properties.normalizeLabels()) {
			rules.add(new LabelNormalizationRule());
		}
		// Runs after e-mail normalization would be ideal, but the rule normalizes
		// e-mail local parts itself; placed here so repaired names flow into all
		// later reporting.
		if (properties.repairFlippedNames()) {
			rules.add(new FlippedNameRepairRule());
		}
		if (properties.normalizePhoneNumbers()) {
			rules.add(new PhoneNumberNormalizationRule(properties.phoneRegion()));
		}
		if (properties.removeFaxNumbers()) {
			rules.add(new FaxNumberRemovalRule());
		}
		if (properties.removeInvalidPhoneNumbers()) {
			rules.add(new InvalidPhoneNumberRemovalRule(properties.phoneRegion()));
		}
		if (properties.removeDuplicatePhoneNumbers()) {
			rules.add(new DuplicatePhoneNumberRemovalRule());
		}
		if (properties.normalizeEmailAddresses()) {
			rules.add(new EmailNormalizationRule());
		}
		if (properties.removeInvalidEmails()) {
			rules.add(new InvalidEmailRemovalRule());
		}
		if (properties.removeDuplicateEmailAddresses()) {
			rules.add(new DuplicateEmailRemovalRule());
		}
		// Birthday extraction must run before any note removal so a birthday hiding
		// in a note is promoted to BDAY before the note disappears.
		if (properties.extractBirthdays()) {
			rules.add(new BirthdayExtractionRule());
		}
		if (properties.removeSocialNetworkNotes()) {
			rules.add(new SocialNetworkNoteRemovalRule());
		}
		if (properties.cleanUrls()) {
			rules.add(new UrlCleanupRule());
		}
		if (!properties.removeCustomFields().isEmpty()) {
			rules.add(new CustomFieldRemovalRule(properties.removeCustomFields()));
		}
		if (!properties.removeOrganizations().isEmpty()) {
			rules.add(new OrganizationRemovalRule(properties.removeOrganizations()));
		}
		if (properties.removeSelfOrganizations()) {
			rules.add(new SelfOrganizationRemovalRule());
		}
		// Must run after all organization rules: the decision depends on the final
		// state (a title orphaned by organization removal is dangling too).
		if (properties.removeDanglingTitles()) {
			rules.add(new DanglingTitleRemovalRule());
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
		boolean empty = isDeletableEmptyContact(vcard);
		if (log.isTraceEnabled()) {
			log.trace("Cleaned contact '{}': changed={}, empty={}", displayName(vcard), changed, empty);
		}
		return new CleaningResult(changed, empty);
	}

	/**
	 * Whether the contact carries no information at all and empty-contact deletion is
	 * enabled. Evaluated by the orchestration <em>after</em> all cleaning passes, so a
	 * contact whose last phone number was a removed office line is caught too.
	 * @param vcard the contact to check
	 * @return {@code true} if the contact should be deleted
	 */
	public boolean isDeletableEmptyContact(VCard vcard) {
		return properties.deleteEmptyContacts() && isEmpty(vcard);
	}

	/**
	 * A contact is considered empty only when it carries no information at all beyond a
	 * bare name: no phone number, no e-mail address, no birthday, no postal address, no
	 * website, no note and no organization. A birthday-only contact, for example, still
	 * serves as a reminder and is kept.
	 */
	private boolean isEmpty(VCard vcard) {
		return vcard.getTelephoneNumbers().isEmpty() && vcard.getEmails().isEmpty() && vcard.getBirthday() == null
				&& vcard.getAddresses().isEmpty() && vcard.getUrls().isEmpty() && vcard.getNotes().isEmpty()
				&& vcard.getOrganizations().isEmpty();
	}

	private String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null) ? vcard.getFormattedName().getValue() : "<unnamed>";
	}

}
