package com.patbaumgartner.contactscleaner.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly scheduled cleanup. Active only when
 * {@code contacts-cleaner.scheduler.enabled=true} (the {@code server} profile enables
 * it), running by default at 03:00 in the configured zone.
 *
 * <p>
 * Cron and zone are configurable via {@code contacts-cleaner.scheduler.cron} and
 * {@code contacts-cleaner.scheduler.zone}.
 */
@Component
@ConditionalOnProperty(prefix = "contacts-cleaner.scheduler", name = "enabled", havingValue = "true")
class ContactsCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(ContactsCleanupScheduler.class);

	private final ContactsCleanupService cleanupService;

	ContactsCleanupScheduler(ContactsCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	@Scheduled(cron = "${contacts-cleaner.scheduler.cron:0 0 3 * * *}",
			zone = "${contacts-cleaner.scheduler.zone:Europe/Zurich}")
	void runNightlyCleanup() {
		log.info("Nightly contacts cleanup triggered");
		cleanupService.cleanAllAccounts();
	}

}
