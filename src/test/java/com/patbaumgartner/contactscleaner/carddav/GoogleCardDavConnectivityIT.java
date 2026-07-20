package com.patbaumgartner.contactscleaner.carddav;

import java.time.Duration;
import java.util.List;

import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Read-only connectivity check against the real Google CardDAV endpoint. Runs only when
 * credentials are provided via environment variables:
 *
 * <pre>{@code CONTACTS_CLEANER_IT_EMAIL=jane.doe@gmail.com \ CONTACTS_CLEANER_IT_APP_PASSWORD="abcd efgh ijkl mnop" \ ./mvnw verify }</pre>
 *
 * The test never writes: it fetches the address book and asserts that the response is
 * parseable.
 */
@EnabledIfEnvironmentVariable(named = "CONTACTS_CLEANER_IT_EMAIL", matches = ".+")
@EnabledIfEnvironmentVariable(named = "CONTACTS_CLEANER_IT_APP_PASSWORD", matches = ".+")
class GoogleCardDavConnectivityIT {

	@Test
	void fetchesAddressBookFromGoogle() {
		CardDavProperties properties = new CardDavProperties("https://www.google.com", Duration.ofSeconds(10),
				Duration.ofSeconds(60), Duration.ofMillis(200));
		RestClient restClient = RestClient.builder()
			.baseUrl(properties.baseUrl())
			.requestFactory(ClientHttpRequestFactoryBuilder.httpComponents()
				.build(HttpClientSettings.defaults()
					.withConnectTimeout(properties.connectTimeout())
					.withReadTimeout(properties.readTimeout())))
			.build();
		GoogleCardDavClient client = new GoogleCardDavClient(restClient, new MultistatusParser(), properties);

		GoogleAccount account = new GoogleAccount("integration-test", System.getenv("CONTACTS_CLEANER_IT_EMAIL"),
				System.getenv("CONTACTS_CLEANER_IT_APP_PASSWORD"), true, true);

		List<AddressBookEntry> entries = client.fetchAllContacts(account);

		assertThat(entries).isNotNull();
		entries.stream().limit(3).forEach((entry) -> {
			assertThat(entry.href()).isNotBlank();
			assertThat(entry.vcard()).contains("BEGIN:VCARD");
		});
	}

}
