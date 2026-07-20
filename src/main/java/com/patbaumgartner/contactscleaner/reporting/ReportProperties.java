package com.patbaumgartner.contactscleaner.reporting;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * HTML report configuration, bound from {@code contacts-cleaner.report}.
 *
 * @param enabled whether to write the single-page HTML report after each run
 * @param directory target directory for the report files (created if missing)
 */
@Validated
@ConfigurationProperties(prefix = "contacts-cleaner.report")
public record ReportProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("reports") @NotBlank String directory) {
}
