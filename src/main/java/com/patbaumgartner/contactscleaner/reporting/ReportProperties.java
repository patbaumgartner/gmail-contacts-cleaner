package com.patbaumgartner.contactscleaner.reporting;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * HTML report configuration, bound from {@code contacts-cleaner.report}.
 *
 * @param enabled whether to write the single-page HTML report after each run
 * @param directory target directory for the report files (created if missing)
 * @param retain how many timestamped reports to keep; the oldest are deleted after each
 * run so a nightly server-mode container cannot fill its volume.
 * {@code cleanup-report-latest.html} is never pruned
 */
@Validated
@ConfigurationProperties(prefix = "contacts-cleaner.report")
public record ReportProperties(@DefaultValue("true") boolean enabled,
		@DefaultValue("reports") @NotBlank String directory, @DefaultValue("30") @Min(1) int retain) {
}
