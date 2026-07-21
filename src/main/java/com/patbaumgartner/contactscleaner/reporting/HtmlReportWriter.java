package com.patbaumgartner.contactscleaner.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.patbaumgartner.contactscleaner.orchestration.AccountCleanupResult;
import com.patbaumgartner.contactscleaner.orchestration.CleanupRunCompleted;
import com.patbaumgartner.contactscleaner.orchestration.ContactChange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Renders every cleanup run as a self-contained single-page HTML report — inline CSS, no
 * external assets, a live text filter, and red/green diff lines per contact. The perfect
 * companion for dry runs: open the file, skim what <em>would</em> change, then flip
 * {@code dry-run} off.
 *
 * <p>
 * Written to {@code <directory>/cleanup-report-<timestamp>.html} and additionally to
 * {@code <directory>/cleanup-report-latest.html} for bookmarking.
 */
@Component
public class HtmlReportWriter {

	private static final Logger log = LoggerFactory.getLogger(HtmlReportWriter.class);

	private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
		.withZone(ZoneId.systemDefault());

	private static final DateTimeFormatter DISPLAY_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
		.withZone(ZoneId.systemDefault());

	private final ReportProperties properties;

	public HtmlReportWriter(ReportProperties properties) {
		this.properties = properties;
	}

	@EventListener
	public void onCleanupRunCompleted(CleanupRunCompleted event) {
		if (!this.properties.enabled()) {
			return;
		}
		try {
			Path directory = Path.of(this.properties.directory());
			Files.createDirectories(directory);
			String html = render(event);
			Path report = directory.resolve("cleanup-report-" + FILE_TIMESTAMP.format(event.completedAt()) + ".html");
			Files.writeString(report, html, StandardCharsets.UTF_8);
			Files.writeString(directory.resolve("cleanup-report-latest.html"), html, StandardCharsets.UTF_8);
			log.info("HTML report written to {}", report.toAbsolutePath());
		}
		catch (IOException ex) {
			log.error("Failed to write HTML report", ex);
		}
	}

	private String render(CleanupRunCompleted event) {
		StringBuilder body = new StringBuilder();
		int totalChanges = event.results().stream().mapToInt((result) -> result.changes().size()).sum();
		boolean anyDryRun = event.results().stream().anyMatch(AccountCleanupResult::dryRun);

		body.append("<header><h1>🧹 Gmail Contacts Cleaner</h1><p>")
			.append(escape(DISPLAY_TIMESTAMP.format(event.completedAt())))
			.append(anyDryRun ? " <span class=\"chip dry\">DRY RUN — nothing was written</span>" : "")
			.append("</p></header>");

		body.append("<input id=\"filter\" type=\"search\" placeholder=\"Filter contacts… (name, number, e-mail)\" ")
			.append("oninput=\"filterRows(this.value)\" autofocus>");

		for (AccountCleanupResult result : event.results()) {
			renderAccount(body, result);
		}
		if (totalChanges == 0) {
			body.append("<p class=\"allclean\">✨ Address books already spotless — no changes.</p>");
		}
		return PAGE_TEMPLATE.replace("%%BODY%%", body.toString());
	}

	private void renderAccount(StringBuilder body, AccountCleanupResult result) {
		body.append("<section><h2>").append(escape(result.accountName()));
		body.append(result.successful() ? " <span class=\"chip ok\">OK</span>"
				: " <span class=\"chip fail\">FAILED</span>");
		if (result.dryRun()) {
			body.append(" <span class=\"chip dry\">dry run</span>");
		}
		body.append("</h2>");

		body.append("<div class=\"stats\">")
			.append(stat(result.totalContacts(), "contacts"))
			.append(stat(result.updatedContacts(), "updated"))
			.append(stat(result.deletedContacts(), "deleted"))
			.append(stat(result.duplicateCandidates().size(), "duplicate candidates"))
			.append(importStats(result))
			.append(profilePhotoStats(result))
			.append(stat(result.durationMs(), "ms"))
			.append("</div>");

		if (!result.successful()) {
			body.append("<p class=\"error\">").append(escape(result.message())).append("</p>");
		}

		for (ContactChange change : result.changes()) {
			body.append("<article class=\"change ")
				.append(change.type().name().toLowerCase(java.util.Locale.ROOT))
				.append("\">");
			body.append("<h3>").append(badge(change.type())).append(escape(change.contactName())).append("</h3>");
			change.removedLines()
				.forEach((line) -> body.append("<div class=\"line del\">− ").append(escape(line)).append("</div>"));
			change.addedLines()
				.forEach((line) -> body.append("<div class=\"line add\">+ ").append(escape(line)).append("</div>"));
			body.append("</article>");
		}

		if (!result.duplicateCandidates().isEmpty()) {
			body.append("<details><summary>🔍 ")
				.append(result.duplicateCandidates().size())
				.append(" possible duplicates (not merged — review manually)</summary>");
			result.duplicateCandidates()
				.forEach((candidate) -> body.append("<div class=\"line dup\">")
					.append(escape(candidate.firstContact()))
					.append(" ↔ ")
					.append(escape(candidate.secondContact()))
					.append(" <span class=\"reason\">(")
					.append(escape(candidate.reason()))
					.append(")</span></div>"));
			body.append("</details>");
		}
		body.append("</section>");
	}

	private String badge(ContactChange.Type type) {
		return switch (type) {
			case UPDATED -> "<span class=\"chip upd\">updated</span> ";
			case DELETED -> "<span class=\"chip del\">deleted</span> ";
		};
	}

	private String stat(long value, String label) {
		return "<div class=\"stat\"><b>" + value + "</b><span>" + label + "</span></div>";
	}

	private String importStats(AccountCleanupResult result) {
		if (result.otherContactsImport()
			.equals(com.patbaumgartner.contactscleaner.peopleapi.OtherContactsImportResult.EMPTY)) {
			return "";
		}
		var importResult = result.otherContactsImport();
		return stat(importResult.promoted(), "Other Contacts promoted")
				+ stat(importResult.skipped(), "Other Contacts skipped")
				+ stat(importResult.failed(), "Other Contacts failed");
	}

	private String profilePhotoStats(AccountCleanupResult result) {
		if (result.googleProfilePhotos()
			.equals(com.patbaumgartner.contactscleaner.peopleapi.GoogleProfilePhotoResult.EMPTY)) {
			return "";
		}
		var photoResult = result.googleProfilePhotos();
		return stat(photoResult.removed(), "contact photos replaced")
				+ stat(photoResult.skipped(), "contact photos retained")
				+ stat(photoResult.failed(), "contact photo updates failed");
	}

	private String escape(String value) {
		return (value == null) ? ""
				: value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static final String PAGE_TEMPLATE = """
			<!doctype html>
			<html lang="en">
			<head>
			<meta charset="utf-8">
			<meta name="viewport" content="width=device-width, initial-scale=1">
			<title>Gmail Contacts Cleaner — Report</title>
			<style>
			:root { color-scheme: light dark; }
			* { box-sizing: border-box; }
			body { font: 15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; margin: 0 auto;
			       max-width: 900px; padding: 2rem 1rem 4rem; background: #f6f8fa; color: #1f2328; }
			@media (prefers-color-scheme: dark) { body { background: #0d1117; color: #e6edf3; } }
			header h1 { margin: 0 0 .25rem; font-size: 1.6rem; }
			header p { margin: 0 0 1.5rem; opacity: .7; }
			#filter { width: 100%; padding: .6rem .9rem; font-size: 1rem; border-radius: 10px;
			          border: 1px solid #d0d7de; margin-bottom: 1.5rem; background: inherit; color: inherit; }
			section { background: rgba(127,127,127,.06); border: 1px solid rgba(127,127,127,.25);
			          border-radius: 12px; padding: 1rem 1.25rem; margin-bottom: 1.5rem; }
			h2 { margin: .2rem 0 .8rem; font-size: 1.2rem; }
			.stats { display: flex; gap: .75rem; flex-wrap: wrap; margin-bottom: 1rem; }
			.stat { background: rgba(127,127,127,.1); border-radius: 10px; padding: .4rem .9rem;
			        text-align: center; }
			.stat b { display: block; font-size: 1.15rem; }
			.stat span { font-size: .75rem; opacity: .7; }
			.chip { font-size: .7rem; font-weight: 600; padding: .15rem .55rem; border-radius: 999px;
			        vertical-align: middle; text-transform: uppercase; letter-spacing: .03em; }
			.chip.ok  { background: #dafbe1; color: #116329; }
			.chip.fail, .chip.del { background: #ffebe9; color: #cf222e; }
			.chip.dry { background: #fff8c5; color: #7d4e00; }
			.chip.upd { background: #ddf4ff; color: #0969da; }
			.chip.mrg { background: #fbefff; color: #8250df; }
			@media (prefers-color-scheme: dark) {
			  .chip.ok { background:#1c3a24; color:#7ee2a8; } .chip.fail, .chip.del { background:#4a1519; color:#ff9492; }
			  .chip.dry { background:#3a3418; color:#e3c655; } .chip.upd { background:#122d47; color:#79c0ff; }
			  .chip.mrg { background:#31234a; color:#d2a8ff; } }
			article.change { border-top: 1px solid rgba(127,127,127,.2); padding: .6rem 0; }
			article.change h3 { margin: 0 0 .3rem; font-size: 1rem; }
			.line { font: 13px/1.55 ui-monospace, SFMono-Regular, Menlo, monospace; padding: .05rem .5rem;
			        border-radius: 6px; white-space: pre-wrap; word-break: break-all; }
			.line.del { background: rgba(255,129,130,.18); }
			.line.add { background: rgba(74,194,107,.18); }
			.line.dup { padding: .15rem .5rem; }
			.reason { opacity: .6; }
			.error { color: #cf222e; font-weight: 600; }
			.allclean { text-align: center; font-size: 1.2rem; padding: 2rem; }
			details summary { cursor: pointer; margin: .5rem 0; }
			.hidden { display: none; }
			</style>
			</head>
			<body>
			%%BODY%%
			<script>
			function filterRows(query) {
			  const q = query.trim().toLowerCase();
			  document.querySelectorAll("article.change, .line.dup").forEach((el) => {
			    el.classList.toggle("hidden", q !== "" && !el.textContent.toLowerCase().includes(q));
			  });
			}
			</script>
			</body>
			</html>
			""";

}
