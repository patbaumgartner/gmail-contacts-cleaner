package com.patbaumgartner.contactscleaner.peopleapi;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * People API client for account-specific contact operations unavailable through CardDAV.
 */
@Component
class GooglePeopleApiClient implements OtherContactsClient, ContactPhotoClient {

	private static final String COPY_MASK = "names,emailAddresses,phoneNumbers";

	private static final long PHOTO_DELETE_INTERVAL_MS = 800;

	private static final Logger log = LoggerFactory.getLogger(GooglePeopleApiClient.class);

	private final RestClient restClient;

	private final PeopleApiProperties properties;

	private long nextPhotoDeleteAt;

	GooglePeopleApiClient(@Qualifier("peopleApiRestClient") RestClient peopleApiRestClient,
			PeopleApiProperties properties) {
		this.restClient = peopleApiRestClient;
		this.properties = properties;
	}

	@Override
	public OtherContactsImportResult importOtherContacts(GoogleAccount account, Set<String> knownEmailAddresses,
			Set<String> knownPhoneNumbers) {
		if (!account.hasOtherContactsImportCredentials()) {
			throw new OtherContactsException("Other contacts import for account '%s' requires OAuth client ID, "
					+ "client secret, and refresh token".formatted(account.name()));
		}
		try {
			String accessToken = refreshAccessToken(account);
			int discovered = 0;
			int promoted = 0;
			int skipped = 0;
			int failed = 0;
			Set<String> knownEmails = new HashSet<>(knownEmailAddresses);
			Set<String> knownPhones = new HashSet<>(knownPhoneNumbers);
			String pageToken = null;
			do {
				OtherContactsPage page = listOtherContacts(accessToken, pageToken);
				List<Person> contacts = (page.otherContacts() != null) ? page.otherContacts() : List.of();
				discovered += contacts.size();
				for (Person contact : contacts) {
					if (matchesRegularContact(contact, knownEmails, knownPhones)) {
						skipped++;
					}
					else {
						try {
							copyToMyContacts(accessToken, contact.resourceName());
							promoted++;
							addContactIdentifiers(contact, knownEmails, knownPhones);
						}
						catch (RestClientException ex) {
							failed++;
							log.warn("Could not promote Other contact '{}' — continuing without retry",
									contact.resourceName(), ex);
						}
					}
				}
				log.info("Other contacts import progress: {} discovered, {} promoted, {} skipped, {} failed",
						discovered, promoted, skipped, failed);
				pageToken = page.nextPageToken();
				if ((pageToken == null || pageToken.isBlank()) && page.totalSize() != null
						&& discovered < page.totalSize()) {
					log.warn(
							"Google reported {} Other contacts but returned only {} without a continuation token for account '{}'",
							page.totalSize(), discovered, account.name());
				}
			}
			while (pageToken != null && !pageToken.isBlank());
			return new OtherContactsImportResult(discovered, promoted, skipped, failed);
		}
		catch (RestClientException ex) {
			throw new OtherContactsException(
					"Failed to import Other contacts for account '%s'".formatted(account.name()), ex);
		}
	}

	@Override
	public GoogleProfilePhotoResult preferGoogleProfilePhotos(GoogleAccount account) {
		validateOAuthCredentials(account);
		try {
			String accessToken = refreshAccessToken(account);
			int scanned = 0;
			int removed = 0;
			int skipped = 0;
			int failed = 0;
			String pageToken = null;
			do {
				ConnectionsPage page = listConnections(accessToken, pageToken);
				List<Person> contacts = (page.connections() != null) ? page.connections() : List.of();
				for (Person contact : contacts) {
					scanned++;
					if (!hasContactPhotoAndGoogleProfilePhoto(contact)) {
						skipped++;
						continue;
					}
					try {
						throttlePhotoDeletes();
						deleteContactPhoto(accessToken, contact.resourceName());
						removed++;
					}
					catch (HttpClientErrorException.NotFound ex) {
						skipped++;
						log.debug("Contact-specific photo for '{}' was already absent", contact.resourceName());
					}
					catch (HttpClientErrorException.TooManyRequests ex) {
						failed++;
						log.warn(
								"Google rate-limited profile photo updates for account '{}' after {} removals; "
										+ "stopping this pass to avoid further failed requests",
								account.name(), removed);
						return new GoogleProfilePhotoResult(scanned, removed, skipped, failed);
					}
					catch (RestClientException ex) {
						failed++;
						log.warn("Could not remove contact-specific photo for '{}' — continuing without retry",
								contact.resourceName(), ex);
					}
				}
				log.info("Google profile photo progress: {} scanned, {} contact photos removed, {} skipped, {} failed",
						scanned, removed, skipped, failed);
				pageToken = page.nextPageToken();
			}
			while (pageToken != null && !pageToken.isBlank());
			return new GoogleProfilePhotoResult(scanned, removed, skipped, failed);
		}
		catch (RestClientException ex) {
			throw new OtherContactsException(
					"Failed to prefer Google profile photos for account '%s'".formatted(account.name()), ex);
		}
	}

	private void validateOAuthCredentials(GoogleAccount account) {
		if (!account.hasOtherContactsImportCredentials()) {
			throw new OtherContactsException("People API access for account '%s' requires OAuth client ID, "
					+ "client secret, and refresh token".formatted(account.name()));
		}
	}

	private String refreshAccessToken(GoogleAccount account) {
		MultiValueMap<String, String> request = new LinkedMultiValueMap<>();
		request.add("client_id", account.oauthClientId());
		request.add("client_secret", account.oauthClientSecret());
		request.add("refresh_token", account.oauthRefreshToken());
		request.add("grant_type", "refresh_token");
		TokenResponse response = restClient.post()
			.uri(properties.tokenUrl())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.body(request)
			.retrieve()
			.body(TokenResponse.class);
		if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
			throw new OtherContactsException("OAuth token response did not contain an access token");
		}
		return response.accessToken();
	}

	private OtherContactsPage listOtherContacts(String accessToken, String pageToken) {
		return restClient.get().uri((builder) -> {
			builder.path("/v1/otherContacts")
				.queryParam("readMask", "metadata,emailAddresses,phoneNumbers")
				.queryParam("pageSize", 1000);
			if (pageToken != null) {
				builder.queryParam("pageToken", pageToken);
			}
			return builder.build();
		}).headers((headers) -> headers.setBearerAuth(accessToken)).retrieve().body(OtherContactsPage.class);
	}

	private ConnectionsPage listConnections(String accessToken, String pageToken) {
		return restClient.get().uri((builder) -> {
			builder.path("/v1/people/me/connections")
				.queryParam("personFields", "photos,metadata")
				.queryParam("pageSize", 1000);
			if (pageToken != null) {
				builder.queryParam("pageToken", pageToken);
			}
			return builder.build();
		}).headers((headers) -> headers.setBearerAuth(accessToken)).retrieve().body(ConnectionsPage.class);
	}

	private boolean hasContactPhotoAndGoogleProfilePhoto(Person contact) {
		boolean hasContactPhoto = contact.photos().stream().anyMatch((photo) -> photo.sourceType().equals("CONTACT"));
		boolean hasGoogleProfilePhoto = contact.photos()
			.stream()
			.anyMatch((photo) -> !Boolean.TRUE.equals(photo.defaultPhoto())
					&& (photo.sourceType().equals("PROFILE") || photo.sourceType().equals("DOMAIN_PROFILE")));
		return hasContactPhoto && hasGoogleProfilePhoto;
	}

	private boolean matchesRegularContact(Person contact, Set<String> knownEmails, Set<String> knownPhones) {
		return contact.emailAddresses()
			.stream()
			.map(EmailAddress::value)
			.map(this::normalizeEmail)
			.anyMatch(knownEmails::contains)
				|| contact.phoneNumbers()
					.stream()
					.map(PhoneNumber::value)
					.map(this::normalizePhone)
					.anyMatch(knownPhones::contains);
	}

	private void addContactIdentifiers(Person contact, Set<String> knownEmails, Set<String> knownPhones) {
		contact.emailAddresses()
			.stream()
			.map(EmailAddress::value)
			.map(this::normalizeEmail)
			.filter((value) -> !value.isEmpty())
			.forEach(knownEmails::add);
		contact.phoneNumbers()
			.stream()
			.map(PhoneNumber::value)
			.map(this::normalizePhone)
			.filter((value) -> !value.isEmpty())
			.forEach(knownPhones::add);
	}

	private String normalizeEmail(String value) {
		return (value != null) ? value.trim().toLowerCase(Locale.ROOT) : "";
	}

	private String normalizePhone(String value) {
		String digits = (value != null) ? value.replaceAll("\\D", "") : "";
		return digits.startsWith("00") ? digits.substring(2) : digits;
	}

	private void copyToMyContacts(String accessToken, String resourceName) {
		if (resourceName == null || resourceName.isBlank()) {
			throw new OtherContactsException("People API returned an Other contact without a resource name");
		}
		restClient.post()
			.uri((builder) -> builder.path("/v1/")
				.path(resourceName)
				.path(":copyOtherContactToMyContactsGroup")
				.build())
			.headers((headers) -> headers.setBearerAuth(accessToken))
			.contentType(MediaType.APPLICATION_JSON)
			.body(new CopyOtherContactRequest(COPY_MASK))
			.retrieve()
			.toBodilessEntity();
	}

	private void deleteContactPhoto(String accessToken, String resourceName) {
		if (resourceName == null || resourceName.isBlank()) {
			throw new OtherContactsException("People API returned a contact without a resource name");
		}
		restClient.delete()
			.uri((builder) -> builder.path("/v1/").path(resourceName).path(":deleteContactPhoto").build())
			.headers((headers) -> headers.setBearerAuth(accessToken))
			.retrieve()
			.toBodilessEntity();
	}

	private void throttlePhotoDeletes() {
		long delay;
		synchronized (this) {
			long now = System.currentTimeMillis();
			delay = nextPhotoDeleteAt - now;
			nextPhotoDeleteAt = Math.max(nextPhotoDeleteAt, now) + PHOTO_DELETE_INTERVAL_MS;
		}
		waitForPhotoDeleteSlot(delay);
	}

	private void waitForPhotoDeleteSlot(long delay) {
		if (delay > 0) {
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new OtherContactsException("Interrupted while throttling Google profile photo updates", ex);
			}
		}
	}

	private record TokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	private record OtherContactsPage(@JsonProperty("otherContacts") List<Person> otherContacts,
			@JsonProperty("nextPageToken") String nextPageToken, @JsonProperty("totalSize") Integer totalSize) {
	}

	private record ConnectionsPage(@JsonProperty("connections") List<Person> connections,
			@JsonProperty("nextPageToken") String nextPageToken) {
	}

	private record Person(@JsonProperty("resourceName") String resourceName,
			@JsonProperty("emailAddresses") List<EmailAddress> emailAddresses,
			@JsonProperty("phoneNumbers") List<PhoneNumber> phoneNumbers, @JsonProperty("photos") List<Photo> photos) {

		private Person {
			emailAddresses = (emailAddresses != null) ? List.copyOf(emailAddresses) : List.of();
			phoneNumbers = (phoneNumbers != null) ? List.copyOf(phoneNumbers) : List.of();
			photos = (photos != null) ? List.copyOf(photos) : List.of();
		}
	}

	private record EmailAddress(@JsonProperty("value") String value) {
	}

	private record PhoneNumber(@JsonProperty("value") String value) {
	}

	private record Photo(@JsonProperty("metadata") PhotoMetadata metadata,
			@JsonProperty("default") Boolean defaultPhoto) {

		private String sourceType() {
			return (metadata != null && metadata.source() != null && metadata.source().type() != null)
					? metadata.source().type() : "";
		}
	}

	private record PhotoMetadata(@JsonProperty("source") PhotoSource source) {
	}

	private record PhotoSource(@JsonProperty("type") String type) {
	}

	private record CopyOtherContactRequest(@JsonProperty("copyMask") String copyMask) {
	}

}