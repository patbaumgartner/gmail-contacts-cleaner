package com.patbaumgartner.contactscleaner.cleaning;

import java.util.Iterator;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.property.StructuredName;

/**
 * Removes junk name suffixes — parenthesized fragments like {@code (JIRA)},
 * {@code (bkrt)} or {@code (D3\GASTRO\CATE SERV)} that phone and messenger imports shove
 * into the suffix field. Real honorific suffixes ({@code Jr.}, {@code PMP}, {@code MSc})
 * are never parenthesized and are kept.
 */
final class JunkNameSuffixRemovalRule implements VCardCleaningRule {

	private static final Pattern PARENTHESIZED = Pattern.compile("^\\s*[(\\[{].*[)\\]}]\\s*$");

	@Override
	public boolean apply(VCard vcard) {
		StructuredName name = vcard.getStructuredName();
		if (name == null || name.getSuffixes().isEmpty()) {
			return false;
		}
		boolean changed = false;
		for (Iterator<String> iterator = name.getSuffixes().iterator(); iterator.hasNext();) {
			String suffix = iterator.next();
			if (suffix != null && PARENTHESIZED.matcher(suffix).matches()) {
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

}
