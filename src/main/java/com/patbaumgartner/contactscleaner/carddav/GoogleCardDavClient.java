package com.patbaumgartner.contactscleaner.carddav;

import java.net.URI;
import java.util.ArrayList;
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

	private static final HttpMethod PROPFIND = HttpMethod.valueOf("PROPFIND");

	/** Number of vCards requested per addressbook-multiget REPORT. */
	private static final int MULTIGET_BATCH_SIZE = 100;

	private static final MediaType TEXT_VCARD = MediaType.parseMediaType("text/vcard; charset=utf-8");

	/**
	 * PROPFIND body listing every resource of the collection with its etag. Google does
	 * not reliably support {@code addressbook-query} with inline address data, so
	 * contacts are listed first and fetched via {@code addressbook-multiget} — the same
	 * two-step flow iOS/macOS use.
	 */
	private static final String PROPFIND_ETAGS = """
			<?xml version="1.0" encoding="UTF-8"?>
			<d:propfind xmlns:d="DAV:">
			  <d:prop>
			    <d:getetag/>
			  </d:prop>
			</d:propfind>
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
		log.debug("Listing address book for account '{}' at {}", account.name(), path);
		try {
			List<AddressBookEntry> refs = listResources(account, path);
			log.info("Address book of account '{}' lists {} contacts", account.name(), refs.size());
			List<AddressBookEntry> entries = new ArrayList<>(refs.size());
			for (int from = 0; from < refs.size(); from += MULTIGET_BATCH_SIZE) {
				List<AddressBookEntry> batch = refs.subList(from, Math.min(from + MULTIGET_BATCH_SIZE, refs.size()));
				entries.addAll(multiget(account, path, batch));
			}
			log.info("Fetched {} contacts for account '{}'", entries.size(), account.name());
			return entries;
		}
		catch (RestClientException ex) {
			throw new CardDavException("Failed to fetch contacts for account '%s' — check e-mail and app password"
				.formatted(account.name()), ex);
		}
	}

	/** Lists all vCard resources (href + etag) of the collection via PROPFIND Depth:1. */
	private List<AddressBookEntry> listResources(GoogleAccount account, String path) {
		String multistatus = restClient.method(PROPFIND)
			.uri(path)
			.headers((headers) -> authenticate(headers, account))
			.header("Depth", "1")
			.contentType(MediaType.APPLICATION_XML)
			.body(PROPFIND_ETAGS)
			.retrieve()
			.body(String.class);
		return multistatusParser.parseResourceRefs((multistatus != null) ? multistatus : "");
	}

	/** Fetches the vCard payloads of the given resources via addressbook-multiget. */
	private List<AddressBookEntry> multiget(GoogleAccount account, String path, List<AddressBookEntry> refs) {
		StringBuilder body = new StringBuilder("""
				<?xml version="1.0" encoding="UTF-8"?>
				<card:addressbook-multiget xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
				  <d:prop>
				    <d:getetag/>
				    <card:address-data/>
				  </d:prop>
				""");
		for (AddressBookEntry ref : refs) {
			body.append("  <d:href>").append(escapeXml(ref.href())).append("</d:href>\n");
		}
		body.append("</card:addressbook-multiget>\n");

		String multistatus = restClient.method(REPORT)
			.uri(path)
			.headers((headers) -> authenticate(headers, account))
			.header("Depth", "1")
			.contentType(MediaType.APPLICATION_XML)
			.body(body.toString())
			.retrieve()
			.body(String.class);
		return multistatusParser.parse((multistatus != null) ? multistatus : "");
	}

	private String escapeXml(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
