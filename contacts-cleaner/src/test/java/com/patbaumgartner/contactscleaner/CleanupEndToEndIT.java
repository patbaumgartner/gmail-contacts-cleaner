package com.patbaumgartner.contactscleaner;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.patbaumgartner.contactscleaner.orchestration.AccountCleanupResult;
import com.patbaumgartner.contactscleaner.orchestration.ContactsCleanupService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test: boots the full Spring context and runs a real cleanup
 * against an embedded fake CardDAV server. Exercises the actual HTTP stack — including
 * the WebDAV {@code REPORT} verb, Basic authentication, etag-guarded {@code PUT}s and
 * opt-in {@code DELETE}s.
 */
@SpringBootTest(properties = { "contacts-cleaner.carddav.request-delay=0ms",
		"contacts-cleaner.cleaning.delete-empty-contacts=true" })
@ActiveProfiles("integration-test")
class CleanupEndToEndIT {

	private static HttpServer server;

	private static final Map<String, String> updates = new ConcurrentHashMap<>();

	private static final List<String> deletions = new CopyOnWriteArrayList<>();

	private static final String MULTISTATUS = """
			<?xml version="1.0" encoding="UTF-8"?>
			<d:multistatus xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
			  <d:response>
			    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/dirty</d:href>
			    <d:propstat>
			      <d:status>HTTP/1.1 200 OK</d:status>
			      <d:prop>
			        <d:getetag>"etag-dirty"</d:getetag>
			        <card:address-data>BEGIN:VCARD
			VERSION:3.0
			FN:Jane Doe
			TEL:0041 44 668 18 00
			TEL:+41446681800
			EMAIL:Jane.Doe@GMAIL.com
			END:VCARD</card:address-data>
			      </d:prop>
			    </d:propstat>
			  </d:response>
			  <d:response>
			    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/empty</d:href>
			    <d:propstat>
			      <d:status>HTTP/1.1 200 OK</d:status>
			      <d:prop>
			        <d:getetag>"etag-empty"</d:getetag>
			        <card:address-data>BEGIN:VCARD
			VERSION:3.0
			FN:Ghost Contact
			END:VCARD</card:address-data>
			      </d:prop>
			    </d:propstat>
			  </d:response>
			</d:multistatus>
			""";

	@Autowired
	private ContactsCleanupService cleanupService;

	@BeforeAll
	static void startFakeCardDavServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/carddav/v1/principals/", CleanupEndToEndIT::handle);
		server.start();
	}

	@AfterAll
	static void stopFakeCardDavServer() {
		server.stop(0);
	}

	@DynamicPropertySource
	static void carddavProperties(DynamicPropertyRegistry registry) {
		registry.add("contacts-cleaner.carddav.base-url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
		registry.add("contacts-cleaner.accounts[0].name", () -> "e2e");
		registry.add("contacts-cleaner.accounts[0].email", () -> "jane.doe@gmail.com");
		registry.add("contacts-cleaner.accounts[0].app-password", () -> "abcd efgh ijkl mnop");
	}

	private static void handle(HttpExchange exchange) throws IOException {
		String authorization = exchange.getRequestHeaders().getFirst("Authorization");
		if (authorization == null || !authorization.startsWith("Basic ")) {
			exchange.sendResponseHeaders(401, -1);
			return;
		}
		String method = exchange.getRequestMethod();
		String path = exchange.getRequestURI().getPath();
		switch (method) {
			case "REPORT" -> respond(exchange, 207, MULTISTATUS);
			case "PUT" -> {
				updates.put(path, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				respond(exchange, 204, "");
			}
			case "DELETE" -> {
				deletions.add(path);
				respond(exchange, 204, "");
			}
			default -> exchange.sendResponseHeaders(405, -1);
		}
	}

	private static void respond(HttpExchange exchange, int status, String body) throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().add("Content-Type", "application/xml; charset=utf-8");
		exchange.sendResponseHeaders(status, (bytes.length > 0) ? bytes.length : -1);
		if (bytes.length > 0) {
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}
	}

	@Test
	void cleansAddressBookEndToEnd() {
		List<AccountCleanupResult> results = this.cleanupService.cleanAllAccounts();

		assertThat(results).singleElement().satisfies((result) -> {
			assertThat(result.successful()).isTrue();
			assertThat(result.totalContacts()).isEqualTo(2);
			assertThat(result.updatedContacts()).isEqualTo(1);
			assertThat(result.deletedContacts()).isEqualTo(1);
		});

		assertThat(updates).containsOnlyKeys("/carddav/v1/principals/jane.doe@gmail.com/lists/default/dirty");
		assertThat(updates.values().iterator().next()).contains("TEL:+41446681800")
			.contains("EMAIL:jane.doe@gmail.com")
			.doesNotContain("0041");
		assertThat(deletions).containsExactly("/carddav/v1/principals/jane.doe@gmail.com/lists/default/empty");
	}

}
