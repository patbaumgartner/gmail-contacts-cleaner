package com.patbaumgartner.contactscleaner.carddav;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class MultistatusParserTests {

	private final MultistatusParser parser = new MultistatusParser();

	private static final String MULTISTATUS = """
			<?xml version="1.0" encoding="UTF-8"?>
			<d:multistatus xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
			  <d:response>
			    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123</d:href>
			    <d:propstat>
			      <d:status>HTTP/1.1 200 OK</d:status>
			      <d:prop>
			        <d:getetag>"2011-03-01T10:00:00.000-08:00"</d:getetag>
			        <card:address-data>BEGIN:VCARD
			VERSION:3.0
			FN:Jane Doe
			TEL:+41446681800
			END:VCARD</card:address-data>
			      </d:prop>
			    </d:propstat>
			  </d:response>
			  <d:response>
			    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/</d:href>
			    <d:propstat>
			      <d:status>HTTP/1.1 404 Not Found</d:status>
			      <d:prop/>
			    </d:propstat>
			  </d:response>
			</d:multistatus>
			""";

	@Test
	void parsesVcardResourcesAndSkipsCollectionResponses() {
		List<AddressBookEntry> entries = parser.parse(MULTISTATUS);

		assertThat(entries).hasSize(1);
		AddressBookEntry entry = entries.getFirst();
		assertThat(entry.href()).isEqualTo("/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123");
		assertThat(entry.etag()).isEqualTo("\"2011-03-01T10:00:00.000-08:00\"");
		assertThat(entry.vcard()).startsWith("BEGIN:VCARD").contains("FN:Jane Doe").endsWith("END:VCARD");
	}

	@Test
	void parsesResourceRefsSkippingTheCollectionItself() {
		String propfind = """
				<?xml version="1.0" encoding="UTF-8"?>
				<d:multistatus xmlns:d="DAV:">
				  <d:response>
				    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/</d:href>
				    <d:propstat><d:status>HTTP/1.1 200 OK</d:status><d:prop/></d:propstat>
				  </d:response>
				  <d:response>
				    <d:href>/carddav/v1/principals/jane.doe%40gmail.com/lists/default/abc123</d:href>
				    <d:propstat>
				      <d:status>HTTP/1.1 200 OK</d:status>
				      <d:prop><d:getetag>"etag-1"</d:getetag></d:prop>
				    </d:propstat>
				  </d:response>
				</d:multistatus>
				""";

		List<AddressBookEntry> refs = parser.parseResourceRefs(propfind);

		assertThat(refs).hasSize(1);
		assertThat(refs.getFirst().href()).endsWith("/abc123");
		assertThat(refs.getFirst().etag()).isEqualTo("\"etag-1\"");
		assertThat(refs.getFirst().vcard()).isNull();
	}

	@Test
	void returnsEmptyListForEmptyMultistatus() {
		assertThat(parser.parse("""
				<?xml version="1.0" encoding="UTF-8"?>
				<d:multistatus xmlns:d="DAV:"/>
				""")).isEmpty();
	}

	@Test
	void rejectsMalformedXml() {
		assertThatExceptionOfType(CardDavException.class).isThrownBy(() -> parser.parse("this is not XML"))
			.withMessageContaining("multistatus");
	}

	@Test
	void rejectsDoctypeDeclarationsToPreventXxe() {
		assertThatExceptionOfType(CardDavException.class).isThrownBy(() -> parser
			.parse("<?xml version=\"1.0\"?><!DOCTYPE d [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><d/>"));
	}

}
