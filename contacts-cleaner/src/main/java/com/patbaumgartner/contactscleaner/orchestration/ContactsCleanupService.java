package com.patbaumgartner.contactscleaner.orchestration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.patbaumgartner.contactscleaner.account.AccountsProperties;
import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import com.patbaumgartner.contactscleaner.carddav.AddressBookEntry;
import com.patbaumgartner.contactscleaner.carddav.CardDavClient;
import com.patbaumgartner.contactscleaner.cleaning.ContactCleaner;
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

	private final ApplicationEventPublisher eventPublisher;

	/** Guards against overlapping runs (e.g. a startup run during a scheduled run). */
	private final AtomicBoolean runInProgress = new AtomicBoolean(false);

	ContactsCleanupService(AccountsProperties accountsProperties, CardDavClient cardDavClient,
			ContactCleaner contactCleaner, ApplicationEventPublisher eventPublisher) {
		this.accountsProperties = accountsProperties;
		this.cardDavClient = cardDavClient;
		this.contactCleaner = contactCleaner;
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
			int updated = 0;
			int deleted = 0;
			for (AddressBookEntry entry : entries) {
				VCard vcard = parse(entry);
				if (vcard == null) {
					continue;
				}
				var result = contactCleaner.clean(vcard);
				if (result.empty()) {
					deleted += deleteContact(account, entry, vcard) ? 1 : 0;
				}
				else if (result.changed()) {
					updated += updateContact(account, entry, vcard) ? 1 : 0;
				}
			}
			long duration = System.currentTimeMillis() - start;
			log.info("Completed cleanup for account '{}': {} contacts, {} updated, {} deleted in {}ms{}",
					account.name(), entries.size(), updated, deleted, duration,
					account.dryRun() ? " (dry run — nothing written)" : "");
			return new AccountCleanupResult(account.name(), true, entries.size(), updated, deleted, account.dryRun(),
					duration, "Cleanup completed");
		}
		catch (RuntimeException ex) {
			long duration = System.currentTimeMillis() - start;
			log.error("Cleanup failed for account '{}' after {}ms", account.name(), duration, ex);
			return AccountCleanupResult.failure(account.name(), duration, ex.getMessage());
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
