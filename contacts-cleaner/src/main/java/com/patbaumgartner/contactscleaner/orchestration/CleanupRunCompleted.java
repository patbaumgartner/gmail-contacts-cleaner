package com.patbaumgartner.contactscleaner.orchestration;

import java.time.Instant;
import java.util.List;

/**
 * Domain event published after a full cleanup run over all enabled accounts. Consumed
 * by the {@code reporting} module; new consumers (e-mail, push notifications, metrics)
 * can subscribe without touching the orchestration logic.
 *
 * @param completedAt when the run finished
 * @param results the per-account results, in configuration order
 */
public record CleanupRunCompleted(Instant completedAt, List<AccountCleanupResult> results) {

	public CleanupRunCompleted {
		results = List.copyOf(results);
	}
}
