package com.patbaumgartner.contactscleaner.carddav;

import java.net.URI;
import java.util.List;

import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * {@link CardDavClient} implementation for Google's CardDAV service
 * ({@code https://www.google.com/carddav/v1/...}, RFC 6352).
 *
 * <p>
 * Contacts are listed with a single {@code addressbook-query} REPORT that returns etags
 * and full vCard payloads in one round trip. Updates and deletions are individual,
 * etag-guarded requests.
 */
@Component
class GoogleCardDavClient implements CardDavClient {

	private static final Logger log = LoggerFactory.getLogger(GoogleCardDavClient.class);

	private static final HttpMethod REPORT = HttpMethod.valueOf("REPORT");

	private static final MediaType TEXT_VCARD = MediaType.parseMediaType("text/vcard; charset=utf-8");

	/**
	 * RFC 6352 addressbook-query asking for the etag and the full address data of every
	 * vCard in the collection.
	 */
	private static final String ADDRESSBOOK_QUERY = """
			<?xml version="1.0" encoding="UTF-8"?>
			<card:addressbook-query xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
			  <d:prop>
			    <d:getetag/>
			    <card:address-data/>
			  </d:prop>
			</card:addressbook-query>
			""";

	private final RestClient restClient;

	private final MultistatusParser multistatusParser;

	private final CardDavProperties properties;

	GoogleCardDavClient(RestClient carddavRestClient, MultistatusParser multistatusParser,
			CardDavProperties properties) {
		this.restClient = carddavRestClient;
		this.multistatusParser = multistatusParser;
		this.properties = properties;
	}

	@Override
	public List<AddressBookEntry> fetchAllContacts(GoogleAccount account) {
		String path = properties.addressBookPath(account.email());
		log.debug("Fetching address book for account '{}' from {}", account.name(), path);
		try {
			String multistatus = restClient.method(REPORT)
				.uri(path)
				.headers((headers) -> authenticate(headers, account))
				.header("Depth", "1")
				.contentType(MediaType.APPLICATION_XML)
				.body(ADDRESSBOOK_QUERY)
				.retrieve()
				.body(String.class);
			List<AddressBookEntry> entries = multistatusParser.parse((multistatus != null) ? multistatus : "");
			log.info("Fetched {} contacts for account '{}'", entries.size(), account.name());
			return entries;
		}
		catch (RestClientException ex) {
			throw new CardDavException("Failed to fetch contacts for account '%s' — check e-mail and app password"
				.formatted(account.name()), ex);
		}
	}

	@Override
	public void updateContact(GoogleAccount account, AddressBookEntry entry, String vcard) {
		log.debug("Updating contact {} for account '{}'", entry.href(), account.name());
		try {
			restClient.put()
				.uri(resourceUri(entry))
				.headers((headers) -> conditional(authenticateHeaders(headers, account), entry))
				.contentType(TEXT_VCARD)
				.body(vcard)
				.retrieve()
				.toBodilessEntity();
			pause();
		}
		catch (RestClientException ex) {
			throw new CardDavException(
					"Failed to update contact %s for account '%s'".formatted(entry.href(), account.name()), ex);
		}
	}

	@Override
	public void deleteContact(GoogleAccount account, AddressBookEntry entry) {
		log.debug("Deleting contact {} for account '{}'", entry.href(), account.name());
		try {
			restClient.delete()
				.uri(resourceUri(entry))
				.headers((headers) -> conditional(authenticateHeaders(headers, account), entry))
				.retrieve()
				.toBodilessEntity();
			pause();
		}
		catch (RestClientException ex) {
			throw new CardDavException(
					"Failed to delete contact %s for account '%s'".formatted(entry.href(), account.name()), ex);
		}
	}

	/**
	 * Builds the absolute resource URI from an already percent-encoded href. The href
	 * must not run through URI template expansion, which would double-encode it.
	 */
	private URI resourceUri(AddressBookEntry entry) {
		return URI.create(properties.baseUrl() + entry.href());
	}

	private void authenticate(HttpHeaders headers, GoogleAccount account) {
		headers.setBasicAuth(account.email(), account.appPassword());
	}

	private HttpHeaders authenticateHeaders(HttpHeaders headers, GoogleAccount account) {
		authenticate(headers, account);
		return headers;
	}

	private void conditional(HttpHeaders headers, AddressBookEntry entry) {
		if (entry.etag() != null && !entry.etag().isBlank()) {
			headers.setIfMatch(entry.etag());
		}
	}

	/**
	 * Sleeps briefly between write requests to stay well below Google's rate limits.
	 */
	private void pause() {
		try {
			Thread.sleep(properties.requestDelay());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new CardDavException("Interrupted while throttling CardDAV requests", ex);
		}
	}

}
