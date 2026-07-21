package com.patbaumgartner.contactscleaner.peopleapi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.patbaumgartner.contactscleaner.account.GoogleAccount;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import org.springframework.stereotype.Component;

/**
 * People API client for promoting Other contacts. CardDAV does not expose this
 * collection.
 */
@Component
class GooglePeopleApiClient implements OtherContactsClient {

	private static final String COPY_MASK = "names,emailAddresses,phoneNumbers";

	private final RestClient restClient;

	private final PeopleApiProperties properties;

	GooglePeopleApiClient(@Qualifier("peopleApiRestClient") RestClient peopleApiRestClient,
			PeopleApiProperties properties) {
		this.restClient = peopleApiRestClient;
		this.properties = properties;
	}

	@Override
	public OtherContactsImportResult importOtherContacts(GoogleAccount account) {
		if (!account.hasOtherContactsImportCredentials()) {
			throw new OtherContactsException("Other contacts import for account '%s' requires OAuth client ID, "
					+ "client secret, and refresh token".formatted(account.name()));
		}
		try {
			String accessToken = refreshAccessToken(account);
			int discovered = 0;
			int promoted = 0;
			String pageToken = null;
			do {
				OtherContactsPage page = listOtherContacts(accessToken, pageToken);
				List<Person> contacts = (page.otherContacts() != null) ? page.otherContacts() : List.of();
				discovered += contacts.size();
				for (Person contact : contacts) {
					copyToMyContacts(accessToken, contact.resourceName());
					promoted++;
				}
				pageToken = page.nextPageToken();
			}
			while (pageToken != null && !pageToken.isBlank());
			return new OtherContactsImportResult(discovered, promoted);
		}
		catch (RestClientException ex) {
			throw new OtherContactsException(
					"Failed to import Other contacts for account '%s'".formatted(account.name()), ex);
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
			builder.path("/v1/otherContacts").queryParam("readMask", "metadata").queryParam("pageSize", 1000);
			if (pageToken != null) {
				builder.queryParam("pageToken", pageToken);
			}
			return builder.build();
		}).headers((headers) -> headers.setBearerAuth(accessToken)).retrieve().body(OtherContactsPage.class);
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

	private record TokenResponse(@JsonProperty("access_token") String accessToken) {
	}

	private record OtherContactsPage(@JsonProperty("otherContacts") List<Person> otherContacts,
			@JsonProperty("nextPageToken") String nextPageToken) {
	}

	private record Person(@JsonProperty("resourceName") String resourceName) {
	}

	private record CopyOtherContactRequest(@JsonProperty("copyMask") String copyMask) {
	}

}