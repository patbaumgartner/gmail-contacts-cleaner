package com.patbaumgartner.contactscleaner.reporting;

import com.patbaumgartner.contactscleaner.orchestration.AccountCleanupResult;
import com.patbaumgartner.contactscleaner.orchestration.CleanupRunCompleted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Renders a human-readable summary after every cleanup run. Decoupled from the
 * orchestration workflow through the {@link CleanupRunCompleted} domain event —
 * additional reporters (e-mail, ntfy, metrics) can be added as further listeners
 * without modifying the orchestration module.
 */
@Component
class CleanupSummaryReporter {

	private static final Logger log = LoggerFactory.getLogger(CleanupSummaryReporter.class);

	@EventListener
	void onCleanupRunCompleted(CleanupRunCompleted event) {
		StringBuilder summary = new StringBuilder("Cleanup run summary:");
		for (AccountCleanupResult result : event.results()) {
			summary.append(System.lineSeparator())
				.append("  - %s: %s | %d contacts, %d updated, %d deleted%s (%d ms)%s".formatted(result.accountName(),
						result.successful() ? "OK" : "FAILED", result.totalContacts(), result.updatedContacts(),
						result.deletedContacts(), result.dryRun() ? " [dry run]" : "", result.durationMs(),
						result.successful() ? "" : " — " + result.message()));
		}
		log.info("{}", summary);
	}

}
