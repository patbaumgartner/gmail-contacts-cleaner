package com.patbaumgartner.contactscleaner.cleaning;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ezvcard.VCard;
import ezvcard.property.Telephone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Cross-contact rule: removes phone numbers that appear on many different contacts.
 *
 * <p>
 * A number stored on three or more cards is practically never a personal line — it is the
 * company switchboard, reception or a shared office number that a bulk import copied onto
 * every colleague. Keeping only direct numbers makes the address book dial the right
 * person instead of the front desk.
 *
 * <p>
 * Runs after the per-contact rules, so numbers are already normalized and different
 * spellings of the same switchboard collapse correctly. Disabled by default
 * (destructive); enable via
 * {@code contacts-cleaner.cleaning.remove-shared-phone-numbers=true}. The minimum number
 * of contacts sharing a number before it is considered an office line is configurable via
 * {@code contacts-cleaner.cleaning.shared-phone-number-threshold} (default {@code 3} — a
 * landline shared by a couple is preserved).
 */
@Component
public class SharedPhoneNumberRemover {

	private static final Logger log = LoggerFactory.getLogger(SharedPhoneNumberRemover.class);

	private final CleaningProperties properties;

	public SharedPhoneNumberRemover(CleaningProperties properties) {
		this.properties = properties;
	}

	/**
	 * Removes office/switchboard numbers from the given contacts in place.
	 * @param vcards all contacts of one address book, already individually cleaned
	 * @return the contacts that were modified (identity-based), empty when disabled
	 */
	public Set<VCard> removeSharedNumbers(List<VCard> vcards) {
		if (!this.properties.removeSharedPhoneNumbers()) {
			return Set.of();
		}
		Map<String, Set<VCard>> ownersByNumber = indexOwners(vcards);
		int threshold = this.properties.sharedPhoneNumberThreshold();

		Set<VCard> changed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
		for (VCard vcard : vcards) {
			for (Iterator<Telephone> iterator = vcard.getTelephoneNumbers().iterator(); iterator.hasNext();) {
				String number = iterator.next().getText();
				Set<VCard> owners = (number != null) ? ownersByNumber.get(number) : null;
				if (owners != null && owners.size() >= threshold) {
					log.info("Removing shared office number {} (on {} contacts) from '{}'", number, owners.size(),
							displayName(vcard));
					iterator.remove();
					changed.add(vcard);
				}
			}
		}
		return changed;
	}

	/** Maps each number to the distinct contacts carrying it (same card counts once). */
	private Map<String, Set<VCard>> indexOwners(List<VCard> vcards) {
		Map<String, Set<VCard>> owners = new HashMap<>();
		for (VCard vcard : vcards) {
			Set<String> numbers = new HashSet<>();
			for (Telephone telephone : vcard.getTelephoneNumbers()) {
				if (telephone.getText() != null) {
					numbers.add(telephone.getText());
				}
			}
			for (String number : numbers) {
				owners.computeIfAbsent(number, (key) -> java.util.Collections.newSetFromMap(new IdentityHashMap<>()))
					.add(vcard);
			}
		}
		return owners;
	}

	private String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null && vcard.getFormattedName().getValue() != null)
				? vcard.getFormattedName().getValue() : "<unnamed>";
	}

}
