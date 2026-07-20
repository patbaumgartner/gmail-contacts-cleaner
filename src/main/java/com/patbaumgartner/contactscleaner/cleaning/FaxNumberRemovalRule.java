package com.patbaumgartner.contactscleaner.cleaning;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import ezvcard.VCard;
import ezvcard.parameter.TelephoneType;
import ezvcard.property.RawProperty;
import ezvcard.property.Telephone;

/**
 * Removes fax numbers. It is not 1995: nobody is going to send a fax, and the entries
 * only clutter the dialer. Two representations are covered:
 *
 * <ul>
 * <li>the standard vCard type — {@code TEL;TYPE=FAX} (Google's built-in {@code Work Fax}
 * / {@code Home Fax} labels),</li>
 * <li>Apple-style <em>custom labels</em> — {@code item1.TEL:...} grouped with
 * {@code item1.X-ABLabel:Work Fax}, which Google emits when the label was entered as free
 * text; the label property is removed along with the number.</li>
 * </ul>
 *
 * <p>
 * Destructive, therefore disabled by default; enable via
 * {@code contacts-cleaner.cleaning.remove-fax-numbers=true}.
 */
final class FaxNumberRemovalRule implements VCardCleaningRule {

	/** Matches "Work Fax", "Home Fax", "Fax (Büro)", "Telefax", ... */
	private static final Pattern FAX_LABEL = Pattern.compile("(?i).*\\bfax\\b.*|(?i).*telefax.*");

	@Override
	public boolean apply(VCard vcard) {
		Map<String, RawProperty> faxLabelsByGroup = faxLabelsByGroup(vcard);
		boolean changed = false;
		for (Iterator<Telephone> iterator = vcard.getTelephoneNumbers().iterator(); iterator.hasNext();) {
			Telephone telephone = iterator.next();
			boolean typedFax = telephone.getTypes().contains(TelephoneType.FAX);
			boolean labeledFax = telephone.getGroup() != null && faxLabelsByGroup.containsKey(telephone.getGroup());
			if (typedFax || labeledFax) {
				if (labeledFax) {
					vcard.removeProperty(faxLabelsByGroup.get(telephone.getGroup()));
				}
				iterator.remove();
				changed = true;
			}
		}
		return changed;
	}

	/** Maps group names ({@code item1}) to their fax-ish {@code X-ABLabel} property. */
	private Map<String, RawProperty> faxLabelsByGroup(VCard vcard) {
		Map<String, RawProperty> labels = new HashMap<>();
		for (RawProperty property : vcard.getExtendedProperties()) {
			if (property.getGroup() != null && "X-ABLabel".equalsIgnoreCase(property.getPropertyName())
					&& property.getValue() != null
					&& FAX_LABEL.matcher(property.getValue().trim().toLowerCase(Locale.ROOT)).matches()) {
				labels.put(property.getGroup(), property);
			}
		}
		return labels;
	}

}
