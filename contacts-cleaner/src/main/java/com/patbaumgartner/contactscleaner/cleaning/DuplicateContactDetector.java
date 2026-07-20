package com.patbaumgartner.contactscleaner.cleaning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import ezvcard.VCard;

import org.springframework.stereotype.Component;

/**
 * Report-only detection of duplicate <em>contacts</em> (as opposed to duplicate values
 * within one contact): two cards that most likely describe the same person.
 *
 * <p>
 * Detection strategies, in order of confidence:
 * <ol>
 * <li><strong>Shared phone number</strong> — both contacts carry the same normalized
 * number.</li>
 * <li><strong>Shared e-mail address</strong> — both contacts carry the same normalized
 * address.</li>
 * <li><strong>Near-identical name</strong> — Jaro-Winkler similarity of the formatted
 * names above {@value #NAME_SIMILARITY_THRESHOLD}.</li>
 * </ol>
 *
 * <p>
 * Nothing is ever merged or deleted automatically — resolving a duplicate requires human
 * judgment (which card wins, which data to keep). The candidates are logged in the run
 * summary instead.
 */
@Component
public class DuplicateContactDetector {

	static final double NAME_SIMILARITY_THRESHOLD = 0.94;

	private final CleaningProperties properties;

	public DuplicateContactDetector(CleaningProperties properties) {
		this.properties = properties;
	}

	/**
	 * Detects likely duplicate contacts within one address book.
	 * @param vcards all contacts of the address book
	 * @return duplicate candidates, empty when detection is disabled
	 */
	public List<DuplicateCandidate> detect(List<VCard> vcards) {
		if (!this.properties.detectDuplicateContacts()) {
			return List.of();
		}
		List<DuplicateCandidate> candidates = new ArrayList<>();
		Set<PairKey> reported = new HashSet<>();
		detectSharedValues(vcards, candidates, reported);
		detectSimilarNames(vcards, candidates, reported);
		return List.copyOf(candidates);
	}

	/** Index phone numbers and e-mail addresses; contacts sharing a value match. */
	private void detectSharedValues(List<VCard> vcards, List<DuplicateCandidate> candidates, Set<PairKey> reported) {
		Map<String, Integer> firstOwnerByValue = new HashMap<>();
		for (int i = 0; i < vcards.size(); i++) {
			for (String value : sharedValueKeys(vcards.get(i))) {
				Integer owner = firstOwnerByValue.putIfAbsent(value, i);
				if (owner != null && owner != i && reported.add(new PairKey(owner, i))) {
					candidates.add(new DuplicateCandidate(displayName(vcards.get(owner)), displayName(vcards.get(i)),
							"shared " + (value.startsWith("tel:") ? "phone number " : "e-mail address ")
									+ value.substring(4)));
				}
			}
		}
	}

	private void detectSimilarNames(List<VCard> vcards, List<DuplicateCandidate> candidates, Set<PairKey> reported) {
		List<String> names = vcards.stream().map(this::comparableName).toList();
		for (int i = 0; i < vcards.size(); i++) {
			if (names.get(i).isEmpty()) {
				continue;
			}
			for (int j = i + 1; j < vcards.size(); j++) {
				if (names.get(j).isEmpty() || reported.contains(new PairKey(i, j))) {
					continue;
				}
				double similarity = JaroWinklerSimilarity.of(names.get(i), names.get(j));
				if (similarity >= NAME_SIMILARITY_THRESHOLD) {
					reported.add(new PairKey(i, j));
					candidates.add(new DuplicateCandidate(displayName(vcards.get(i)), displayName(vcards.get(j)),
							"similar name (%.0f%% match)".formatted(similarity * 100)));
				}
			}
		}
	}

	private Set<String> sharedValueKeys(VCard vcard) {
		Set<String> keys = new HashSet<>();
		vcard.getTelephoneNumbers().forEach((telephone) -> {
			String text = telephone.getText();
			if (text != null && !text.isBlank()) {
				keys.add("tel:" + text.replaceAll("[\\s\\-./()]", ""));
			}
		});
		vcard.getEmails().forEach((email) -> {
			String value = email.getValue();
			if (value != null && !value.isBlank()) {
				keys.add("eml:" + value.trim().toLowerCase(Locale.ROOT));
			}
		});
		return keys;
	}

	private String comparableName(VCard vcard) {
		String name = displayName(vcard);
		return "<unnamed>".equals(name) ? "" : name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	private String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null && vcard.getFormattedName().getValue() != null)
				? vcard.getFormattedName().getValue() : "<unnamed>";
	}

	/** Order-independent index pair. */
	private record PairKey(int low, int high) {
		PairKey {
			if (low > high) {
				int swap = low;
				low = high;
				high = swap;
			}
		}
	}

}
