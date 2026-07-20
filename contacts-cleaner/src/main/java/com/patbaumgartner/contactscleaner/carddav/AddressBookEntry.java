package com.patbaumgartner.contactscleaner.carddav;

/**
 * A single vCard resource inside a CardDAV address book, as returned by an
 * {@code addressbook-query} REPORT.
 *
 * @param href the server-relative resource path, used for subsequent {@code PUT} /
 * {@code DELETE} requests
 * @param etag the entity tag of the resource; passed back via {@code If-Match} so
 * concurrent modifications fail fast instead of overwriting each other
 * @param vcard the raw vCard payload (vCard 3.0 as served by Google)
 */
public record AddressBookEntry(String href, String etag, String vcard) {
}
