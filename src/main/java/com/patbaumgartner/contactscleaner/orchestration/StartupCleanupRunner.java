package com.patbaumgartner.contactscleaner.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-shot cleanup at application startup — the default mode for containerized runs
 * driven by an external scheduler (host cron, Kubernetes CronJob, NAS task scheduler).
 *
 * <p>
 * Enabled by default; the long-running {@code server} profile disables it to avoid a
 * duplicate run next to the built-in scheduler. Toggle via
 * {@code contacts-cleaner.startup-run.enabled}.
 */
@Component
@ConditionalOnProperty(prefix = "contacts-cleaner.startup-run", name = "enabled", havingValue = "true",
		matchIfMissing = true)
class StartupCleanupRunner implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(StartupCleanupRunner.class);

	private final ContactsCleanupService cleanupService;

	StartupCleanupRunner(ContactsCleanupService cleanupService) {
		this.cleanupService = cleanupService;
	}

	@Override
	public void run(String... args) {
		log.info("Startup contacts cleanup triggered");
		cleanupService.cleanAllAccounts();
	}

}
