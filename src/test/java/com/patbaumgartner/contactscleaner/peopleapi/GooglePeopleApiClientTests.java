package com.patbaumgartner.contactscleaner.peopleapi;

import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

class GooglePeopleApiClientTests {

	private static final GoogleAccount ACCOUNT = new GoogleAccount("personal", "jane.doe@gmail.com", "app-password",
			true, false, true, "client-id", "client-secret", "refresh-token");

	private MockRestServiceServer server;

	private GooglePeopleApiClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		this.client = new GooglePeopleApiClient(builder.baseUrl("https://people.googleapis.com").build(),
				new PeopleApiProperties("https://people.googleapis.com", "https://oauth2.googleapis.com/token"));
	}

	@Test
	void refreshesTokenPagesThroughOtherContactsAndPromotesEveryContact() {
		this.server.expect(requestTo("https://oauth2.googleapis.com/token"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
			.andExpect(content()
				.string(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("client_id=client-id"),
						org.hamcrest.Matchers.containsString("client_secret=client-secret"),
						org.hamcrest.Matchers.containsString("refresh_token=refresh-token"))))
			.andRespond(withSuccess("{\"access_token\":\"access-token\"}", MediaType.APPLICATION_JSON));
		this.server.expect(requestTo(
				"https://people.googleapis.com/v1/otherContacts?readMask=metadata,emailAddresses,phoneNumbers&pageSize=1000"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(header("Authorization", "Bearer access-token"))
			.andRespond(withSuccess("{\"otherContacts\":[{\"resourceName\":\"otherContacts/one\"}],"
					+ "\"nextPageToken\":\"next-page\"}", MediaType.APPLICATION_JSON));
		this.server
			.expect(requestTo("https://people.googleapis.com/v1/otherContacts/one:copyOtherContactToMyContactsGroup"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header("Authorization", "Bearer access-token"))
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(content().json("{\"copyMask\":\"names,emailAddresses,phoneNumbers\"}"))
			.andRespond(withSuccess());
		this.server.expect(requestTo(
				"https://people.googleapis.com/v1/otherContacts?readMask=metadata,emailAddresses,phoneNumbers&pageSize=1000&pageToken=next-page"))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess("{\"otherContacts\":[{\"resourceName\":\"otherContacts/two\"}]}",
					MediaType.APPLICATION_JSON));
		this.server
			.expect(requestTo("https://people.googleapis.com/v1/otherContacts/two:copyOtherContactToMyContactsGroup"))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json("{\"copyMask\":\"names,emailAddresses,phoneNumbers\"}"))
			.andRespond(withSuccess());

		OtherContactsImportResult result = this.client.importOtherContacts(ACCOUNT);

		assertThat(result).isEqualTo(new OtherContactsImportResult(2, 2));
		this.server.verify();
	}

	@Test
	void skipsExistingContactsAndContinuesAfterAFailedCopy() {
		this.server.expect(requestTo("https://oauth2.googleapis.com/token"))
			.andRespond(withSuccess("{\"access_token\":\"access-token\"}", MediaType.APPLICATION_JSON));
		this.server.expect(requestTo(
				"https://people.googleapis.com/v1/otherContacts?readMask=metadata,emailAddresses,phoneNumbers&pageSize=1000"))
			.andRespond(withSuccess("{\"otherContacts\":["
					+ "{\"resourceName\":\"otherContacts/existing\",\"emailAddresses\":[{\"value\":\"existing@example.test\"}]},"
					+ "{\"resourceName\":\"otherContacts/failing\",\"emailAddresses\":[{\"value\":\"failing@example.test\"}]}]}",
					MediaType.APPLICATION_JSON));
		this.server
			.expect(requestTo(
					"https://people.googleapis.com/v1/otherContacts/failing:copyOtherContactToMyContactsGroup"))
			.andRespond(withServerError());

		OtherContactsImportResult result = this.client.importOtherContacts(ACCOUNT, Set.of("existing@example.test"),
				Set.of());

		assertThat(result).isEqualTo(new OtherContactsImportResult(2, 0, 1, 1));
		this.server.verify();
	}

	@Test
	void rejectsEnabledImportWithoutOAuthCredentials() {
		GoogleAccount account = new GoogleAccount("personal", "jane.doe@gmail.com", "app-password", true, false, true,
				"", "", "");

		assertThatExceptionOfType(OtherContactsException.class)
			.isThrownBy(() -> this.client.importOtherContacts(account))
			.withMessageContaining("requires OAuth client ID");
	}

}