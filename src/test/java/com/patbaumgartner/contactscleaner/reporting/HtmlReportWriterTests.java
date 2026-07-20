package com.patbaumgartner.contactscleaner.reporting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.patbaumgartner.contactscleaner.cleaning.DuplicateCandidate;
import com.patbaumgartner.contactscleaner.orchestration.AccountCleanupResult;
import com.patbaumgartner.contactscleaner.orchestration.CleanupRunCompleted;
import com.patbaumgartner.contactscleaner.orchestration.ContactChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlReportWriterTests {

	@TempDir
	Path reportDirectory;

	private CleanupRunCompleted event() {
		var change = new ContactChange("Jane <Doe>", ContactChange.Type.UPDATED, List.of("TEL 0041 44 668 18 00"),
				List.of("TEL +41446681800"));
		var deletion = new ContactChange("Ghost Contact", ContactChange.Type.DELETED, List.of("FN Ghost Contact"),
				List.of());
		var result = new AccountCleanupResult("personal", true, 42, 1, 0,
				List.of(new DuplicateCandidate("A", "B", "shared phone number +417912345678")),
				List.of(change, deletion), true, 1234, "Cleanup completed");
		return new CleanupRunCompleted(Instant.parse("2026-07-20T02:00:00Z"), List.of(result));
	}

	@Test
	void writesTimestampedAndLatestReport() throws Exception {
		var writer = new HtmlReportWriter(new ReportProperties(true, this.reportDirectory.toString()));

		writer.onCleanupRunCompleted(event());

		Path latest = this.reportDirectory.resolve("cleanup-report-latest.html");
		assertThat(latest).exists();
		try (var files = Files.list(this.reportDirectory)) {
			assertThat(files.filter((file) -> file.getFileName().toString().startsWith("cleanup-report-2"))).hasSize(1);
		}

		String html = Files.readString(latest);
		assertThat(html).contains("DRY RUN")
			.contains("personal")
			.contains("TEL +41446681800")
			.contains("Ghost Contact")
			.contains("shared phone number +417912345678")
			// HTML injection from contact data must be escaped
			.contains("Jane &lt;Doe&gt;")
			.doesNotContain("Jane <Doe>");
	}

	@Test
	void doesNothingWhenDisabled() throws Exception {
		var writer = new HtmlReportWriter(new ReportProperties(false, this.reportDirectory.toString()));

		writer.onCleanupRunCompleted(event());

		try (var files = Files.list(this.reportDirectory)) {
			assertThat(files).isEmpty();
		}
	}

}
