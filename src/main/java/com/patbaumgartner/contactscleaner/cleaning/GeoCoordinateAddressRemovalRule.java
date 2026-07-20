package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Iterator;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.Address;

/**
 * Removes postal addresses that consist of nothing but geo coordinates
 * ({@code 47.392977,8.475039}) — debris from check-in apps and photo geotagging that
 * ended up in the address book. A coordinate pair is not a postal address: no human reads
 * it, no navigation app needs it there.
 *
 * <p>
 * Only addresses whose <em>entire content</em> is a latitude/longitude pair are removed;
 * an address that also carries a street or city is kept untouched.
 */
final class GeoCoordinateAddressRemovalRule implements VCardCleaningRule {

	/** "47.392977,8.475039" / "37.3317, -122.0307" — degrees with decimal fractions. */
	private static final Pattern COORDINATE_PAIR = Pattern.compile("^-?\\d{1,3}\\.\\d+\\s*,\\s*-?\\d{1,3}\\.\\d+$");

	@Override
	public boolean apply(VCard vcard) {
		boolean changed = false;
		for (Iterator<Address> iterator = vcard.getAddresses().iterator(); iterator.hasNext();) {
			if (isOnlyCoordinates(iterator.next())) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

	private boolean isOnlyCoordinates(Address address) {
		StringBuilder content = new StringBuilder();
		for (String component : new String[] { address.getPoBox(), address.getExtendedAddress(),
				address.getStreetAddress(), address.getLocality(), address.getRegion(), address.getPostalCode(),
				address.getCountry() }) {
			if (component != null && !component.isBlank()) {
				if (!content.isEmpty()) {
					content.append(' ');
				}
				content.append(component.trim());
			}
		}
		return !content.isEmpty() && COORDINATE_PAIR.matcher(content.toString()).matches();
	}

}
