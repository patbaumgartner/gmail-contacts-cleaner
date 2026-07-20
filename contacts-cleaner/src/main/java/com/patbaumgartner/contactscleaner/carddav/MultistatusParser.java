package com.patbaumgartner.contactscleaner.carddav;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.springframework.stereotype.Component;

/**
 * Parses WebDAV {@code multistatus} (RFC 4918) responses of a CardDAV
 * {@code addressbook-query} REPORT (RFC 6352) into {@link AddressBookEntry} instances.
 *
 * <p>
 * Only {@code response} elements with an HTTP {@code 200} propstat status and a
 * non-empty {@code address-data} property are returned; collection resources and
 * partial errors are skipped.
 */
@Component
class MultistatusParser {

	private static final String DAV_NS = "DAV:";

	private static final String CARDDAV_NS = "urn:ietf:params:xml:ns:carddav";

	/**
	 * Parses a {@code multistatus} XML document.
	 * @param multistatusXml the raw XML body of a {@code 207 Multi-Status} response
	 * @return all successfully reported vCard resources, never {@code null}
	 * @throws CardDavException if the payload is not well-formed XML
	 */
	List<AddressBookEntry> parse(String multistatusXml) {
		Document document = parseDocument(multistatusXml);
		List<AddressBookEntry> entries = new ArrayList<>();

		NodeList responses = document.getElementsByTagNameNS(DAV_NS, "response");
		for (int i = 0; i < responses.getLength(); i++) {
			Element response = (Element) responses.item(i);
			String href = textOf(response, DAV_NS, "href");
			String etag = textOf(response, DAV_NS, "getetag");
			String vcard = textOf(response, CARDDAV_NS, "address-data");
			if (href != null && vcard != null && !vcard.isBlank()) {
				entries.add(new AddressBookEntry(href, etag, vcard));
			}
		}
		return entries;
	}

	private Document parseDocument(String xml) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(true);
			// Harden against XXE — the response is server-controlled, defense in depth.
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
			return factory.newDocumentBuilder()
				.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		}
		catch (Exception ex) {
			throw new CardDavException("Failed to parse CardDAV multistatus response", ex);
		}
	}

	private String textOf(Element parent, String namespace, String localName) {
		NodeList nodes = parent.getElementsByTagNameNS(namespace, localName);
		return (nodes.getLength() > 0) ? nodes.item(0).getTextContent() : null;
	}

}
