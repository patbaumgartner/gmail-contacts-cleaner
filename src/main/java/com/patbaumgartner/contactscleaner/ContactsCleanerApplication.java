package com.patbaumgartner.contactscleaner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

/**
 * Google Contacts Cleaner — cleans and deduplicates Google Contacts across multiple
 * accounts via the CardDAV protocol, authenticated with per-account app passwords.
 *
 * <p>
 * The application is organized as a <a href="https://spring.io/projects/spring-modulith">
 * Spring Modulith</a> with the following modules:
 * <ul>
 * <li>{@code account} — multi-account configuration and credentials</li>
 * <li>{@code carddav} — Google CardDAV protocol client (fetch, update, delete)</li>
 * <li>{@code cleaning} — pure, side-effect free vCard cleaning rules</li>
 * <li>{@code orchestration} — per-account cleanup workflow, scheduler and one-shot
 * runner</li>
 * <li>{@code reporting} — cleanup outcome reporting via domain events</li>
 * </ul>
 */
@Modulithic(systemName = "Gmail Contacts Cleaner", sharedModules = "config")
@SpringBootApplication
@ConfigurationPropertiesScan
public class ContactsCleanerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ContactsCleanerApplication.class, args);
	}

}
