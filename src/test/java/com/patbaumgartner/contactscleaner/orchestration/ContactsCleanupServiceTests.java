package com.patbaumgartner.contactscleaner.orchestration;

import java.util.List;

import com.patbaumgartner.contactscleaner.account.AccountsProperties;
import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import com.patbaumgartner.contactscleaner.carddav.AddressBookEntry;
import com.patbaumgartner.contactscleaner.carddav.CardDavClient;
import com.patbaumgartner.contactscleaner.carddav.CardDavException;
import com.patbaumgartner.contactscleaner.cleaning.CleaningProperties;
import com.patbaumgartner.contactscleaner.cleaning.ContactCleaner;
import com.patbaumgartner.contactscleaner.cleaning.DomainResolution;
import com.patbaumgartner.contactscleaner.cleaning.DuplicateContactDetector;
import com.patbaumgartner.contactscleaner.cleaning.EmailDomainVerifier;
import com.patbaumgartner.contactscleaner.cleaning.OrganizationCanonicalizer;
import com.patbaumgartner.contactscleaner.cleaning.SharedPhoneNumberRemover;
import com.patbaumgartner.contactscleaner.peopleapi.ContactIdentifiers;
import com.patbaumgartner.contactscleaner.peopleapi.OtherContactsClient;
import com.patbaumgartner.contactscleaner.peopleapi.OtherContactsImportResult;
import com.patbaumgartner.contactscleaner.peopleapi.ContactNameClient;
import com.patbaumgartner.contactscleaner.peopleapi.ContactPhotoClient;
import com.patbaumgartner.contactscleaner.peopleapi.GoogleContactNameResult;
import com.patbaumgartner.contactscleaner.peopleapi.GoogleProfilePhotoResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContactsCleanupServiceTests {

	private static final String DIRTY_VCARD = """
			BEGIN:VCARD
			VERSION:3.0
			FN: Jane Doe\u0020
			TEL:0041 44 668 18 00
			TEL:+41446681800
			EMAIL:Jane.Doe@GMAIL.com
			END:VCARD
			""";

	private static final String CLEAN_VCARD = """
			BEGIN:VCARD
			VERSION:3.0
			FN:Jane Doe
			TEL:+41446681800
			EMAIL:contact@gmail.com
			END:VCARD
			""";

	private static final String EMPTY_VCARD = """
			BEGIN:VCARD
			VERSION:3.0
			FN:Ghost Contact
			END:VCARD
			""";

	@Mock
	private CardDavClient cardDavClient;

	@Mock
	private OtherContactsClient otherContactsClient;

	@Mock
	private ContactPhotoClient contactPhotoClient;

	@Mock
	private ContactNameClient contactNameClient;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	private ContactsCleanupService service(GoogleAccount account, boolean deleteEmptyContacts) {
		var accounts = new AccountsProperties(List.of(account));
		var properties = CleaningProperties.builder().deleteEmptyContacts(deleteEmptyContacts).build();
		return service(accounts, properties);
	}

	private ContactsCleanupService service(AccountsProperties accounts,
			com.patbaumgartner.contactscleaner.cleaning.CleaningProperties properties) {
		return new ContactsCleanupService(accounts, this.cardDavClient, this.otherContactsClient,
				this.contactPhotoClient, this.contactNameClient, new ContactCleaner(properties),
				new DuplicateContactDetector(properties), new SharedPhoneNumberRemover(properties),
				new EmailDomainVerifier(properties, (domain) -> DomainResolution.DELIVERABLE),
				new OrganizationCanonicalizer(properties), this.eventPublisher);
	}

	private static GoogleAccount account(boolean dryRun) {
		return GoogleAccount.builder("personal", "jane.doe@gmail.com", "app-password").dryRun(dryRun).build();
	}

	private static GoogleAccount.Builder oauthAccount(boolean dryRun) {
		return GoogleAccount.builder("personal", "jane.doe@gmail.com", "app-password")
			.dryRun(dryRun)
			.oauth("client-id", "client-secret", "refresh-token");
	}

	@Test
	void updatesOnlyChangedContacts() {
		GoogleAccount account = account(false);
		var dirty = new AddressBookEntry("/contacts/dirty", "\"e1\"", DIRTY_VCARD);
		var clean = new AddressBookEntry("/contacts/clean", "\"e2\"", CLEAN_VCARD);
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of(dirty, clean));

		List<AccountCleanupResult> results = service(account, false).cleanAllAccounts();

		assertThat(results).singleElement().satisfies((result) -> {
			assertThat(result.successful()).isTrue();
			assertThat(result.totalContacts()).isEqualTo(2);
			assertThat(result.updatedContacts()).isEqualTo(1);
			assertThat(result.deletedContacts()).isZero();
		});

		ArgumentCaptor<String> vcardCaptor = ArgumentCaptor.forClass(String.class);
		verify(this.cardDavClient).updateContact(eq(account), eq(dirty), vcardCaptor.capture());
		assertThat(vcardCaptor.getValue()).contains("TEL:+41446681800")
			.contains("EMAIL:jane.doe@gmail.com")
			.doesNotContain("0041");
		verify(this.cardDavClient, never()).updateContact(eq(account), eq(clean), anyString());
	}

	@Test
	void continuesWhenOneContactUpdateIsRejected() {
		GoogleAccount account = account(false);
		var rejected = new AddressBookEntry("/contacts/rejected", "\"e1\"", DIRTY_VCARD);
		var updated = new AddressBookEntry("/contacts/updated", "\"e2\"", DIRTY_VCARD);
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of(rejected, updated));
		doThrow(new CardDavException("400 Bad Request")).when(this.cardDavClient)
			.updateContact(eq(account), eq(rejected), anyString());

		List<AccountCleanupResult> results = service(account, false).cleanAllAccounts();

		assertThat(results).singleElement().satisfies((result) -> {
			assertThat(result.successful()).isTrue();
			assertThat(result.updatedContacts()).isEqualTo(1);
			assertThat(result.changes()).singleElement();
		});
		verify(this.cardDavClient).updateContact(eq(account), eq(rejected), anyString());
		verify(this.cardDavClient).updateContact(eq(account), eq(updated), anyString());
	}

	@Test
	void deletesEmptyContactsWhenEnabled() {
		GoogleAccount account = account(false);
		var empty = new AddressBookEntry("/contacts/empty", "\"e3\"", EMPTY_VCARD);
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of(empty));

		List<AccountCleanupResult> results = service(account, true).cleanAllAccounts();

		assertThat(results.getFirst().deletedContacts()).isEqualTo(1);
		verify(this.cardDavClient).deleteContact(account, empty);
	}

	@Test
	void keepsEmptyContactsByDefault() {
		GoogleAccount account = account(false);
		var empty = new AddressBookEntry("/contacts/empty", "\"e3\"", EMPTY_VCARD);
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of(empty));

		List<AccountCleanupResult> results = service(account, false).cleanAllAccounts();

		assertThat(results.getFirst().deletedContacts()).isZero();
		verify(this.cardDavClient, never()).deleteContact(any(), any());
	}

	@Test
	void dryRunComputesChangesButWritesNothing() {
		GoogleAccount account = account(true);
		var dirty = new AddressBookEntry("/contacts/dirty", "\"e1\"", DIRTY_VCARD);
		var empty = new AddressBookEntry("/contacts/empty", "\"e3\"", EMPTY_VCARD);
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of(dirty, empty));

		List<AccountCleanupResult> results = service(account, true).cleanAllAccounts();

		assertThat(results).singleElement().satisfies((result) -> {
			assertThat(result.dryRun()).isTrue();
			assertThat(result.updatedContacts()).isEqualTo(1);
			assertThat(result.deletedContacts()).isEqualTo(1);
		});
		verify(this.cardDavClient, never()).updateContact(any(), any(), anyString());
		verify(this.cardDavClient, never()).deleteContact(any(), any());
	}

	@Test
	void importsOtherContactsAfterTakingACardDavSnapshot() {
		GoogleAccount account = oauthAccount(false).importOtherContacts(true).build();
		when(this.otherContactsClient.importOtherContacts(eq(account), any(ContactIdentifiers.class)))
			.thenReturn(new OtherContactsImportResult(2, 1, 1, 0));
		when(this.cardDavClient.fetchAllContacts(account))
			.thenReturn(List.of(new AddressBookEntry("/contacts/dirty", "\"e1\"", DIRTY_VCARD)));

		service(account, false).cleanAllAccounts();

		ArgumentCaptor<ContactIdentifiers> knownContacts = ArgumentCaptor.forClass(ContactIdentifiers.class);
		verify(this.otherContactsClient).importOtherContacts(eq(account), knownContacts.capture());
		assertThat(knownContacts.getValue().emailAddresses()).containsExactly("jane.doe@gmail.com");
		assertThat(knownContacts.getValue().phoneNumbers()).containsExactly("41446681800");
		verify(this.cardDavClient, times(2)).fetchAllContacts(account);
	}

	@Test
	void skipsOtherContactsImportDuringDryRun() {
		GoogleAccount account = oauthAccount(true).importOtherContacts(true).build();
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of());

		service(account, false).cleanAllAccounts();

		verify(this.otherContactsClient, never()).importOtherContacts(any(), any(ContactIdentifiers.class));
	}

	@Test
	void prefersGoogleProfilePhotosAndRefreshesTheCardDavSnapshot() {
		GoogleAccount account = oauthAccount(false).preferGoogleProfilePhotos(true).build();
		when(this.contactPhotoClient.preferGoogleProfilePhotos(account))
			.thenReturn(new GoogleProfilePhotoResult(3, 1, 2, 0));
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of());

		List<AccountCleanupResult> results = service(account, false).cleanAllAccounts();

		verify(this.contactPhotoClient).preferGoogleProfilePhotos(account);
		verify(this.cardDavClient, times(2)).fetchAllContacts(account);
		assertThat(results.getFirst().googleProfilePhotos()).isEqualTo(new GoogleProfilePhotoResult(3, 1, 2, 0));
	}

	@Test
	void skipsGoogleProfilePhotoPreferenceDuringDryRun() {
		GoogleAccount account = oauthAccount(true).preferGoogleProfilePhotos(true).build();
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of());

		service(account, false).cleanAllAccounts();

		verify(this.contactPhotoClient, never()).preferGoogleProfilePhotos(any());
	}

	@Test
	void repairsGoogleContactNamesAndRefreshesTheCardDavSnapshot() {
		GoogleAccount account = oauthAccount(false).repairGoogleContactDisplayNames(true).build();
		when(this.contactNameClient.repairCommaFormattedContactNames(account))
			.thenReturn(new GoogleContactNameResult(3, 1, 2, 0));
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of());

		List<AccountCleanupResult> results = service(account, false).cleanAllAccounts();

		verify(this.contactNameClient).repairCommaFormattedContactNames(account);
		verify(this.cardDavClient, times(2)).fetchAllContacts(account);
		assertThat(results.getFirst().googleContactNames()).isEqualTo(new GoogleContactNameResult(3, 1, 2, 0));
	}

	@Test
	void skipsGoogleContactNameRepairDuringDryRun() {
		GoogleAccount account = oauthAccount(true).repairGoogleContactDisplayNames(true).build();
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of());

		service(account, false).cleanAllAccounts();

		verify(this.contactNameClient, never()).repairCommaFormattedContactNames(any());
	}

	@Test
	void isolatesAccountFailuresAndContinues() {
		GoogleAccount failing = GoogleAccount.builder("broken", "broken@gmail.com", "wrong").build();
		GoogleAccount healthy = account(false);
		var accounts = new AccountsProperties(List.of(failing, healthy));
		var service = service(accounts, CleaningProperties.defaults());

		when(this.cardDavClient.fetchAllContacts(failing)).thenThrow(new CardDavException("401 Unauthorized"));
		when(this.cardDavClient.fetchAllContacts(healthy)).thenReturn(List.of());

		List<AccountCleanupResult> results = service.cleanAllAccounts();

		assertThat(results).hasSize(2);
		assertThat(results.getFirst().successful()).isFalse();
		assertThat(results.getFirst().message()).contains("401");
		assertThat(results.getLast().successful()).isTrue();
	}

	@Test
	void reportsAFailedDryRunAsADryRun() {
		GoogleAccount account = account(true);
		when(this.cardDavClient.fetchAllContacts(account)).thenThrow(new CardDavException("401 Unauthorized"));

		List<AccountCleanupResult> results = service(account, false).cleanAllAccounts();

		assertThat(results).singleElement().satisfies((result) -> {
			assertThat(result.successful()).isFalse();
			assertThat(result.dryRun()).isTrue();
			assertThat(result.message()).isEqualTo("401 Unauthorized");
		});
	}

	@Test
	void describesMessagelessFailuresByExceptionType() {
		GoogleAccount account = account(false);
		when(this.cardDavClient.fetchAllContacts(account)).thenThrow(new IllegalStateException());

		List<AccountCleanupResult> results = service(account, false).cleanAllAccounts();

		assertThat(results.getFirst().message()).isEqualTo("IllegalStateException");
	}

	@Test
	void skipsDisabledAccounts() {
		GoogleAccount disabled = GoogleAccount.builder("disabled", "off@gmail.com", "pw").enabled(false).build();
		var accounts = new AccountsProperties(List.of(disabled));
		var service = service(accounts, CleaningProperties.defaults());

		assertThat(service.cleanAllAccounts()).isEmpty();
		verify(this.cardDavClient, never()).fetchAllContacts(any());
	}

	@Test
	void publishesCleanupRunCompletedEvent() {
		GoogleAccount account = account(false);
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of());

		service(account, false).cleanAllAccounts();

		ArgumentCaptor<CleanupRunCompleted> eventCaptor = ArgumentCaptor.forClass(CleanupRunCompleted.class);
		verify(this.eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue().results()).hasSize(1);
	}

	@Test
	void skipsUnparseableVcards() {
		GoogleAccount account = account(false);
		var broken = new AddressBookEntry("/contacts/broken", "\"e9\"", "not a vcard at all");
		when(this.cardDavClient.fetchAllContacts(account)).thenReturn(List.of(broken));

		List<AccountCleanupResult> results = service(account, false).cleanAllAccounts();

		assertThat(results.getFirst().successful()).isTrue();
		verify(this.cardDavClient, never()).updateContact(any(), any(), anyString());
	}

}
