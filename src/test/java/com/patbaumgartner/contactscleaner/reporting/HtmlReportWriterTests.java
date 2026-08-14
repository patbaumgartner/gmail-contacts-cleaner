package com.patbaumgartner.contactscleaner.reporting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.patbaumgartner.contactscleaner.cleaning.DuplicateCandidate;
import com.patbaumgartner.contactscleaner.orchestration.AccountCleanupResult;
import com.patbaumgartner.contactscleaner.orchestration.CleanupRunCompleted;
import com.patbaumgartner.contactscleaner.orchestration.ContactChange;
import com.patbaumgartner.contactscleaner.peopleapi.OtherContactsImportResult;
import com.patbaumgartner.contactscleaner.peopleapi.GoogleContactNameResult;
import com.patbaumgartner.contactscleaner.peopleapi.GoogleProfilePhotoResult;
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
				List.of(change, deletion), new OtherContactsImportResult(4, 2, 1, 1),
				new GoogleProfilePhotoResult(4, 2, 1, 1), true, new GoogleContactNameResult(4, 2, 1, 1), 1234,
				"Cleanup completed");
		return new CleanupRunCompleted(Instant.parse("2026-07-20T02:00:00Z"), List.of(result));
	}

	@Test
	void writesTimestampedAndLatestReport() throws Exception {
		var writer = new HtmlReportWriter(new ReportProperties(true, this.reportDirectory.toString(), 30));

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
			.contains("Other Contacts promoted")
			.contains("Other Contacts skipped")
			.contains("Other Contacts failed")
			.contains("contact photos replaced")
			.contains("contact photos retained")
			.contains("contact photo updates failed")
			.contains("Google contact names repaired")
			.contains("Google contact names retained")
			.contains("Google contact-name updates failed")
			.contains("shared phone number +417912345678")
			// HTML injection from contact data must be escaped
			.contains("Jane &lt;Doe&gt;")
			.doesNotContain("Jane <Doe>");
	}

	@Test
	void doesNothingWhenDisabled() throws Exception {
		var writer = new HtmlReportWriter(new ReportProperties(false, this.reportDirectory.toString(), 30));

		writer.onCleanupRunCompleted(event());

		try (var files = Files.list(this.reportDirectory)) {
			assertThat(files).isEmpty();
		}
	}

	@Test
	void keepsOnlyTheNewestTimestampedReports() throws Exception {
		var writer = new HtmlReportWriter(new ReportProperties(true, this.reportDirectory.toString(), 2));

		List<String> writtenInOrder = new ArrayList<>();
		for (int day = 1; day <= 4; day++) {
			writer.onCleanupRunCompleted(new CleanupRunCompleted(
					Instant.parse("2026-07-0%d T02:00:00Z".formatted(day).replace(" ", "")), event().results()));
			timestampedReports().stream().filter((name) -> !writtenInOrder.contains(name)).forEach(writtenInOrder::add);
		}

		assertThat(writtenInOrder).hasSize(4);
		assertThat(timestampedReports()).containsExactlyInAnyOrderElementsOf(writtenInOrder.subList(2, 4));
		assertThat(this.reportDirectory.resolve("cleanup-report-latest.html")).exists();
	}

	@Test
	void neverPrunesTheLatestReportAlias() throws Exception {
		var writer = new HtmlReportWriter(new ReportProperties(true, this.reportDirectory.toString(), 1));

		writer.onCleanupRunCompleted(new CleanupRunCompleted(Instant.parse("2026-07-01T02:00:00Z"), event().results()));
		writer.onCleanupRunCompleted(new CleanupRunCompleted(Instant.parse("2026-07-02T02:00:00Z"), event().results()));

		assertThat(timestampedReports()).hasSize(1);
		assertThat(this.reportDirectory.resolve("cleanup-report-latest.html")).exists();
	}

	private List<String> timestampedReports() throws Exception {
		try (var files = Files.list(this.reportDirectory)) {
			return files.map((file) -> file.getFileName().toString())
				.filter((name) -> !name.equals("cleanup-report-latest.html"))
				.sorted()
				.toList();
		}
	}

}
