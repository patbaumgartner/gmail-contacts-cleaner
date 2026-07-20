package com.patbaumgartner.contactscleaner.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.patbaumgartner.contactscleaner.account.AccountsProperties;
import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import com.patbaumgartner.contactscleaner.carddav.AddressBookEntry;
import com.patbaumgartner.contactscleaner.carddav.CardDavClient;
import com.patbaumgartner.contactscleaner.cleaning.ContactCleaner;
import com.patbaumgartner.contactscleaner.cleaning.DuplicateCandidate;
import com.patbaumgartner.contactscleaner.cleaning.DuplicateContactDetector;
import com.patbaumgartner.contactscleaner.cleaning.EmailDomainVerifier;
import com.patbaumgartner.contactscleaner.cleaning.OrganizationCanonicalizer;
import com.patbaumgartner.contactscleaner.cleaning.SharedPhoneNumberRemover;
import ezvcard.Ezvcard;
import ezvcard.VCard;
import ezvcard.VCardVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the cleanup workflow across all configured accounts:
 *
 * <ol>
 * <li>fetch every contact of the account's address book via CardDAV,</li>
 * <li>apply the cleaning rules to each vCard,</li>
 * <li>write back only the contacts that actually changed (etag-guarded),</li>
 * <li>optionally delete empty contacts,</li>
 * <li>publish a {@link CleanupRunCompleted} event with the per-account results.</li>
 * </ol>
 *
 * <p>
 * Accounts are isolated from each other: a failure in one account is recorded and the run
 * continues with the next account. Runs are mutually exclusive — a run triggered while
 * another is in progress is skipped.
 */
@Service
public class ContactsCleanupService {

	private static final Logger log = LoggerFactory.getLogger(ContactsCleanupService.class);

	private final AccountsProperties accountsProperties;

	private final CardDavClient cardDavClient;

	private final ContactCleaner contactCleaner;

	private final DuplicateContactDetector duplicateContactDetector;

	private final SharedPhoneNumberRemover sharedPhoneNumberRemover;

	private final EmailDomainVerifier emailDomainVerifier;

	private final OrganizationCanonicalizer organizationCanonicalizer;

	private final ApplicationEventPublisher eventPublisher;

	/** Guards against overlapping runs (e.g. a startup run during a scheduled run). */
	private final AtomicBoolean runInProgress = new AtomicBoolean(false);

	ContactsCleanupService(AccountsProperties accountsProperties, CardDavClient cardDavClient,
			ContactCleaner contactCleaner, DuplicateContactDetector duplicateContactDetector,
			SharedPhoneNumberRemover sharedPhoneNumberRemover, EmailDomainVerifier emailDomainVerifier,
			OrganizationCanonicalizer organizationCanonicalizer, ApplicationEventPublisher eventPublisher) {
		this.accountsProperties = accountsProperties;
		this.cardDavClient = cardDavClient;
		this.contactCleaner = contactCleaner;
		this.duplicateContactDetector = duplicateContactDetector;
		this.sharedPhoneNumberRemover = sharedPhoneNumberRemover;
		this.emailDomainVerifier = emailDomainVerifier;
		this.organizationCanonicalizer = organizationCanonicalizer;
		this.eventPublisher = eventPublisher;
	}

	/**
	 * Runs the cleanup for every enabled account.
	 * @return the per-account results, or an empty list if a run was already in progress
	 */
	public List<AccountCleanupResult> cleanAllAccounts() {
		if (!runInProgress.compareAndSet(false, true)) {
			log.warn("Cleanup already in progress; ignoring new request");
			return List.of();
		}
		try {
			List<GoogleAccount> accounts = accountsProperties.enabledAccounts();
			if (accounts.isEmpty()) {
				log.warn("No enabled accounts configured — nothing to do. "
						+ "Add accounts under 'contacts-cleaner.accounts'.");
				return List.of();
			}
			List<AccountCleanupResult> results = new ArrayList<>();
			for (GoogleAccount account : accounts) {
				results.add(cleanAccount(account));
			}
			eventPublisher.publishEvent(new CleanupRunCompleted(Instant.now(), results));
			return List.copyOf(results);
		}
		finally {
			runInProgress.set(false);
		}
	}

	private AccountCleanupResult cleanAccount(GoogleAccount account) {
		long start = System.currentTimeMillis();
		log.info("Starting cleanup for account '{}'{}", account.name(), account.dryRun() ? " (dry run)" : "");
		try {
			List<AddressBookEntry> entries = cardDavClient.fetchAllContacts(account);

			// Pass 1: parse and clean every contact individually. Snapshots taken
			// before cleaning feed the before/after diff in the HTML report.
			List<AddressBookEntry> parsedEntries = new ArrayList<>();
			List<VCard> vcards = new ArrayList<>();
			Map<VCard, List<String>> snapshots = new IdentityHashMap<>();
			Set<VCard> changedContacts = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
			for (AddressBookEntry entry : entries) {
				VCard vcard = parse(entry);
				if (vcard == null) {
					continue;
				}
				snapshots.put(vcard, VCardSnapshot.of(vcard));
				if (contactCleaner.clean(vcard).changed()) {
					changedContacts.add(vcard);
				}
				parsedEntries.add(entry);
				vcards.add(vcard);
			}

			// Pass 2: cross-contact cleanup — needs the whole address book at once.
			List<ContactChange> changes = new ArrayList<>();
			changedContacts.addAll(sharedPhoneNumberRemover.removeSharedNumbers(vcards));
			changedContacts.addAll(emailDomainVerifier.removeUndeliverableAddresses(vcards));
			changedContacts.addAll(organizationCanonicalizer.canonicalize(vcards));

			// Pass 3: write back. Emptiness is evaluated last so that a contact whose
			// only phone number was a shared office line is deleted (when enabled).
			int updated = 0;
			int deleted = 0;
			List<VCard> survivingContacts = new ArrayList<>();
			for (int i = 0; i < vcards.size(); i++) {
				VCard vcard = vcards.get(i);
				AddressBookEntry entry = parsedEntries.get(i);
				if (contactCleaner.isDeletableEmptyContact(vcard)) {
					deleted += deleteContact(account, entry, vcard) ? 1 : 0;
					changes.add(new ContactChange(VCardSnapshot.displayName(vcard), ContactChange.Type.DELETED,
							snapshots.get(vcard), List.of()));
				}
				else {
					if (changedContacts.contains(vcard)) {
						updated += updateContact(account, entry, vcard) ? 1 : 0;
						changes.add(diff(vcard, snapshots.get(vcard)));
					}
					survivingContacts.add(vcard);
				}
			}
			List<DuplicateCandidate> duplicates = duplicateContactDetector.detect(survivingContacts);
			logDuplicates(account, duplicates);
			long duration = System.currentTimeMillis() - start;
			log.info(
					"Completed cleanup for account '{}': {} contacts, {} updated, {} deleted, "
							+ "{} duplicate candidates in {}ms{}",
					account.name(), entries.size(), updated, deleted, duplicates.size(), duration,
					account.dryRun() ? " (dry run — nothing written)" : "");
			return new AccountCleanupResult(account.name(), true, entries.size(), updated, deleted, duplicates, changes,
					account.dryRun(), duration, "Cleanup completed");
		}
		catch (RuntimeException ex) {
			long duration = System.currentTimeMillis() - start;
			log.error("Cleanup failed for account '{}' after {}ms", account.name(), duration, ex);
			return AccountCleanupResult.failure(account.name(), duration, ex.getMessage());
		}
	}

	/** Computes the before/after property diff of a changed contact. */
	private ContactChange diff(VCard vcard, List<String> before) {
		List<String> after = VCardSnapshot.of(vcard);
		List<String> removed = before.stream().filter((line) -> !after.contains(line)).toList();
		List<String> added = after.stream().filter((line) -> !before.contains(line)).toList();
		return new ContactChange(VCardSnapshot.displayName(vcard), ContactChange.Type.UPDATED, removed, added);
	}

	/**
	 * Duplicate handling is report-only by design: merging requires human judgment, so
	 * candidates are logged for the user to act on in the Google Contacts UI.
	 */
	private void logDuplicates(GoogleAccount account, List<DuplicateCandidate> duplicates) {
		for (DuplicateCandidate duplicate : duplicates) {
			log.info("[{}] Possible duplicate contacts: '{}' and '{}' — {}", account.name(), duplicate.firstContact(),
					duplicate.secondContact(), duplicate.reason());
		}
	}

	private VCard parse(AddressBookEntry entry) {
		VCard vcard = Ezvcard.parse(entry.vcard()).first();
		if (vcard == null) {
			log.warn("Skipping unparseable vCard at {}", entry.href());
		}
		return vcard;
	}

	private boolean updateContact(GoogleAccount account, AddressBookEntry entry, VCard vcard) {
		if (account.dryRun()) {
			log.info("[dry run] Would update contact '{}' ({})", displayName(vcard), entry.href());
			return true;
		}
		cardDavClient.updateContact(account, entry, Ezvcard.write(vcard).version(VCardVersion.V3_0).go());
		return true;
	}

	private boolean deleteContact(GoogleAccount account, AddressBookEntry entry, VCard vcard) {
		if (account.dryRun()) {
			log.info("[dry run] Would delete empty contact '{}' ({})", displayName(vcard), entry.href());
			return true;
		}
		cardDavClient.deleteContact(account, entry);
		return true;
	}

	private String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null) ? vcard.getFormattedName().getValue() : "<unnamed>";
	}

}
