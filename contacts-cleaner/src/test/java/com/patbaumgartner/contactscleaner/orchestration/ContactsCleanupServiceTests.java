package com.patbaumgartner.contactscleaner.orchestration;

import java.util.List;

import com.patbaumgartner.contactscleaner.account.AccountsProperties;
import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import com.patbaumgartner.contactscleaner.carddav.AddressBookEntry;
import com.patbaumgartner.contactscleaner.carddav.CardDavClient;
import com.patbaumgartner.contactscleaner.carddav.CardDavException;
import com.patbaumgartner.contactscleaner.cleaning.CleaningProperties;
import com.patbaumgartner.contactscleaner.cleaning.ContactCleaner;
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
import static org.mockito.Mockito.never;
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
			EMAIL:jane.doe@gmail.com
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
	private ApplicationEventPublisher eventPublisher;

	private ContactsCleanupService service(GoogleAccount account, boolean deleteEmptyContacts) {
		var accounts = new AccountsProperties(List.of(account));
		var cleaner = new ContactCleaner(
				new CleaningProperties(true, true, true, true, true, false, deleteEmptyContacts));
		return new ContactsCleanupService(accounts, this.cardDavClient, cleaner, this.eventPublisher);
	}

	private static GoogleAccount account(boolean dryRun) {
		return new GoogleAccount("personal", "jane.doe@gmail.com", "app-password", true, dryRun);
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
	void isolatesAccountFailuresAndContinues() {
		GoogleAccount failing = new GoogleAccount("broken", "broken@gmail.com", "wrong", true, false);
		GoogleAccount healthy = account(false);
		var accounts = new AccountsProperties(List.of(failing, healthy));
		var cleaner = new ContactCleaner(new CleaningProperties(true, true, true, true, true, false, false));
		var service = new ContactsCleanupService(accounts, this.cardDavClient, cleaner, this.eventPublisher);

		when(this.cardDavClient.fetchAllContacts(failing)).thenThrow(new CardDavException("401 Unauthorized"));
		when(this.cardDavClient.fetchAllContacts(healthy)).thenReturn(List.of());

		List<AccountCleanupResult> results = service.cleanAllAccounts();

		assertThat(results).hasSize(2);
		assertThat(results.getFirst().successful()).isFalse();
		assertThat(results.getFirst().message()).contains("401");
		assertThat(results.getLast().successful()).isTrue();
	}

	@Test
	void skipsDisabledAccounts() {
		GoogleAccount disabled = new GoogleAccount("disabled", "off@gmail.com", "pw", false, false);
		var accounts = new AccountsProperties(List.of(disabled));
		var cleaner = new ContactCleaner(new CleaningProperties(true, true, true, true, true, false, false));
		var service = new ContactsCleanupService(accounts, this.cardDavClient, cleaner, this.eventPublisher);

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
