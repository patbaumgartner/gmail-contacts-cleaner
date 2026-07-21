package com.patbaumgartner.contactscleaner.peopleapi;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Google OAuth and People API endpoints. URLs are overridable for HTTP client tests.
 */
@Validated
@ConfigurationProperties(prefix = "contacts-cleaner.people-api")
public record PeopleApiProperties(@DefaultValue("https://people.googleapis.com") @NotBlank String baseUrl,
		@DefaultValue("https://oauth2.googleapis.com/token") @NotBlank String tokenUrl) {
}