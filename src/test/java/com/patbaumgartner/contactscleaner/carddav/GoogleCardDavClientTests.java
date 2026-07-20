package com.patbaumgartner.contactscleaner.carddav;

import java.time.Duration;
import java.util.List;

import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleCardDavClientTests {

	private static final GoogleAccount ACCOUNT = new GoogleAccount("personal", "jane.doe@gmail.com",
			"abcd efgh ijkl mnop", true, false);

	// "jane.doe@gmail.com:abcd efgh ijkl mnop" base64-encoded
	private static final String BASIC_AUTH_PREFIX = "Basic ";

	private MockRestServiceServer server;

	private GoogleCardDavClient client;

	@BeforeEach
	void setUp() {
		RestClient.Builder builder = RestClient.builder();
		this.server = MockRestServiceServer.bindTo(builder).build();
		CardDavProperties properties = new CardDavProperties("", Duration.ofSeconds(1), Duration.ofSeconds(1),
				Duration.ZERO);
		this.client = new GoogleCardDavClient(builder.build(), new MultistatusParser(), properties);
	}

	@Test
	void fetchesContactsViaPropfindAndMultiget() {
		this.server.expect(requestTo("/carddav/v1/principals/jane.doe@gmail.com/lists/default/"))
			.andExpect(method(HttpMethod.valueOf("PROPFIND")))
			.andExpect(header("Depth", "1"))
			.andExpect(header("Authorization", org.hamcrest.Matchers.startsWith(BASIC_AUTH_PREFIX)))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("getetag")))
			.andRespond(withStatus(HttpStatus.MULTI_STATUS).contentType(MediaType.APPLICATION_XML).body("""
					<?xml version="1.0" encoding="UTF-8"?>
					<d:multistatus xmlns:d="DAV:">
					  <d:response>
					    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/</d:href>
					    <d:propstat><d:status>HTTP/1.1 200 OK</d:status><d:prop/></d:propstat>
					  </d:response>
					  <d:response>
					    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123</d:href>
					    <d:propstat>
					      <d:status>HTTP/1.1 200 OK</d:status>
					      <d:prop><d:getetag>"etag-1"</d:getetag></d:prop>
					    </d:propstat>
					  </d:response>
					</d:multistatus>
					"""));
		this.server.expect(requestTo("/carddav/v1/principals/jane.doe@gmail.com/lists/default/"))
			.andExpect(method(HttpMethod.valueOf("REPORT")))
			.andExpect(content().string(org.hamcrest.Matchers.containsString("addressbook-multiget")))
			.andExpect(content().string(org.hamcrest.Matchers
				.containsString("<d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123</d:href>")))
			.andRespond(withStatus(HttpStatus.MULTI_STATUS).contentType(MediaType.APPLICATION_XML).body("""
					<?xml version="1.0" encoding="UTF-8"?>
					<d:multistatus xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
					  <d:response>
					    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123</d:href>
					    <d:propstat>
					      <d:status>HTTP/1.1 200 OK</d:status>
					      <d:prop>
					        <d:getetag>"etag-1"</d:getetag>
					        <card:address-data>BEGIN:VCARD
					VERSION:3.0
					FN:Jane Doe
					END:VCARD</card:address-data>
					      </d:prop>
					    </d:propstat>
					  </d:response>
					</d:multistatus>
					"""));

		List<AddressBookEntry> entries = this.client.fetchAllContacts(ACCOUNT);

		assertThat(entries).hasSize(1);
		assertThat(entries.getFirst().etag()).isEqualTo("\"etag-1\"");
		this.server.verify();
	}

	@Test
	void emptyAddressBookNeedsNoMultiget() {
		this.server.expect(requestTo("/carddav/v1/principals/jane.doe@gmail.com/lists/default/"))
			.andExpect(method(HttpMethod.valueOf("PROPFIND")))
			.andRespond(withStatus(HttpStatus.MULTI_STATUS).contentType(MediaType.APPLICATION_XML).body("""
					<?xml version="1.0" encoding="UTF-8"?>
					<d:multistatus xmlns:d="DAV:">
					  <d:response>
					    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/</d:href>
					    <d:propstat><d:status>HTTP/1.1 200 OK</d:status><d:prop/></d:propstat>
					  </d:response>
					</d:multistatus>
					"""));

		assertThat(this.client.fetchAllContacts(ACCOUNT)).isEmpty();
		this.server.verify();
	}

	@Test
	void updatesContactWithEtagGuard() {
		this.server.expect(requestTo("/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123"))
			.andExpect(method(HttpMethod.PUT))
			.andExpect(header("If-Match", "\"etag-1\""))
			.andExpect(content().string(org.hamcrest.Matchers.startsWith("BEGIN:VCARD")))
			.andRespond(withSuccess());

		AddressBookEntry entry = new AddressBookEntry(
				"/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123", "\"etag-1\"", "irrelevant");
		this.client.updateContact(ACCOUNT, entry, "BEGIN:VCARD\r\nVERSION:3.0\r\nFN:Jane Doe\r\nEND:VCARD\r\n");

		this.server.verify();
	}

	@Test
	void deletesContactWithEtagGuard() {
		this.server.expect(requestTo("/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123"))
			.andExpect(method(HttpMethod.DELETE))
			.andExpect(header("If-Match", "\"etag-1\""))
			.andRespond(withSuccess());

		AddressBookEntry entry = new AddressBookEntry(
				"/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123", "\"etag-1\"", "irrelevant");
		this.client.deleteContact(ACCOUNT, entry);

		this.server.verify();
	}

	@Test
	void wrapsAuthenticationFailuresInCardDavException() {
		this.server.expect(requestTo("/carddav/v1/principals/jane.doe@gmail.com/lists/default/"))
			.andRespond(withStatus(HttpStatus.UNAUTHORIZED));

		assertThatExceptionOfType(CardDavException.class).isThrownBy(() -> this.client.fetchAllContacts(ACCOUNT))
			.withMessageContaining("app password");
	}

}
