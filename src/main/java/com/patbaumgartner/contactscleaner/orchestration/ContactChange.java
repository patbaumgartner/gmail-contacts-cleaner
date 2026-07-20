package com.patbaumgartner.contactscleaner.orchestration;

import java.util.List;

/**
 * A single contact modification performed (or, in dry-run, simulated) during a cleanup
 * run — the data behind the visual HTML report.
 *
 * @param contactName display name of the contact
 * @param type whether the contact was updated or deleted
 * @param removedLines property lines that disappeared (rendered red in the report)
 * @param addedLines property lines that were added or replaced the removed ones (rendered
 * green)
 */
public record ContactChange(String contactName, Type type, List<String> removedLines, List<String> addedLines) {

	public ContactChange {
		removedLines = List.copyOf(removedLines);
		addedLines = List.copyOf(addedLines);
	}

	/** The kind of modification. */
	public enum Type {

		/** The contact was modified and written back. */
		UPDATED,

		/** The contact carried no information and was deleted. */
		DELETED,

		/** The contact was merged into another card and deleted. */
		MERGED

	}
}
