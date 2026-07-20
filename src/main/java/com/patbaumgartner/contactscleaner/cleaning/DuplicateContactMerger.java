package com.patbaumgartner.contactscleaner.cleaning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import ezvcard.VCard;
import ezvcard.property.Email;
import ezvcard.property.Note;
import ezvcard.property.Telephone;
import ezvcard.property.Url;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

/**
 * Automatically merges contacts that are <em>provably</em> the same person.
 *
 * <p>
 * Two cards qualify for a merge only when <strong>both</strong> signals agree:
 * <ol>
 * <li>their names consist of the same tokens, ignoring case and order —
 * {@code "Max Muster"} and {@code "Muster Max"} match, {@code "Jane Doe"} and
 * {@code "Jane Doe-Muster"} do not, and</li>
 * <li>they share at least one phone number or e-mail address.</li>
 * </ol>
 *
 * Weaker matches (shared value with different names, similar-but-not-equal names) are
 * intentionally <em>not</em> merged — they stay in the report-only
 * {@link DuplicateContactDetector} for human review.
 *
 * <p>
 * The merge is a lossless union into the richest card of the group: phones, e-mails, URLs
 * and notes are combined (deduplicated), a missing birthday, name or organization is
 * taken over from the merged cards. The emptied duplicates are deleted afterwards by the
 * orchestration. Disabled by default; enable via
 * {@code contacts-cleaner.cleaning.merge-duplicate-contacts=true} and start with dry-run.
 */
@Component
public class DuplicateContactMerger {

	private static final Logger log = LoggerFactory.getLogger(DuplicateContactMerger.class);

	private final CleaningProperties properties;

	public DuplicateContactMerger(CleaningProperties properties) {
		this.properties = properties;
	}

	/**
	 * A performed merge.
	 *
	 * @param primary the surviving card, enriched with the union of all properties
	 * @param merged the now-redundant cards to be deleted
	 */
	public record Merge(VCard primary, List<VCard> merged) {

		public Merge {
			merged = List.copyOf(merged);
		}
	}

	/**
	 * Finds and performs all safe merges within one address book.
	 * @param vcards all contacts, already individually cleaned (values normalized)
	 * @return the merges performed, empty when disabled
	 */
	public List<Merge> merge(List<VCard> vcards) {
		if (!this.properties.mergeDuplicateContacts()) {
			return List.of();
		}
		List<Merge> merges = new ArrayList<>();
		for (List<VCard> group : mergeableGroups(vcards)) {
			merges.add(mergeGroup(group));
		}
		return merges;
	}

	/**
	 * Groups contacts by name token set, then keeps subgroups connected by a shared
	 * value.
	 */
	private List<List<VCard>> mergeableGroups(List<VCard> vcards) {
		Map<String, List<VCard>> byNameKey = new HashMap<>();
		for (VCard vcard : vcards) {
			String key = nameKey(vcard);
			if (!key.isEmpty()) {
				byNameKey.computeIfAbsent(key, (k) -> new ArrayList<>()).add(vcard);
			}
		}
		List<List<VCard>> groups = new ArrayList<>();
		for (List<VCard> sameName : byNameKey.values()) {
			if (sameName.size() > 1) {
				groups.addAll(connectedBySharedValue(sameName));
			}
		}
		return groups;
	}

	/** Within a same-name group, connects cards transitively via shared phone/e-mail. */
	private List<List<VCard>> connectedBySharedValue(List<VCard> sameName) {
		int size = sameName.size();
		int[] parent = new int[size];
		for (int i = 0; i < size; i++) {
			parent[i] = i;
		}
		List<Set<String>> values = sameName.stream().map(this::sharedValueKeys).toList();
		for (int i = 0; i < size; i++) {
			for (int j = i + 1; j < size; j++) {
				if (!java.util.Collections.disjoint(values.get(i), values.get(j))) {
					union(parent, i, j);
				}
			}
		}
		Map<Integer, List<VCard>> components = new HashMap<>();
		for (int i = 0; i < size; i++) {
			components.computeIfAbsent(find(parent, i), (k) -> new ArrayList<>()).add(sameName.get(i));
		}
		return components.values().stream().filter((group) -> group.size() > 1).toList();
	}

	private Merge mergeGroup(List<VCard> group) {
		VCard primary = group.stream().max(java.util.Comparator.comparingInt(this::richness)).orElseThrow();
		List<VCard> merged = new ArrayList<>();
		for (VCard other : group) {
			if (other != primary) {
				unionInto(primary, other);
				merged.add(other);
			}
		}
		log.info("Merged {} duplicate card(s) into '{}'", merged.size(), displayName(primary));
		return new Merge(primary, merged);
	}

	/** Copies every property of {@code source} that {@code target} does not have yet. */
	private void unionInto(VCard target, VCard source) {
		Set<String> phones = new HashSet<>();
		target.getTelephoneNumbers().forEach((telephone) -> phones.add(telephone.getText()));
		for (Telephone telephone : source.getTelephoneNumbers()) {
			if (telephone.getText() != null && phones.add(telephone.getText())) {
				target.addTelephoneNumber(telephone);
			}
		}
		Set<String> emails = new HashSet<>();
		target.getEmails().forEach((email) -> emails.add(email.getValue()));
		for (Email email : source.getEmails()) {
			if (email.getValue() != null && emails.add(email.getValue())) {
				target.addEmail(email);
			}
		}
		Set<String> urls = new HashSet<>();
		target.getUrls().forEach((url) -> urls.add(url.getValue()));
		for (Url url : source.getUrls()) {
			if (url.getValue() != null && urls.add(url.getValue())) {
				target.addUrl(url);
			}
		}
		Set<String> notes = new HashSet<>();
		target.getNotes().forEach((note) -> notes.add(note.getValue()));
		for (Note note : source.getNotes()) {
			if (note.getValue() != null && !note.getValue().isBlank() && notes.add(note.getValue())) {
				target.addNote(note.getValue());
			}
		}
		if (target.getBirthday() == null && source.getBirthday() != null) {
			target.setBirthday(source.getBirthday());
		}
		if (target.getOrganizations().isEmpty() && !source.getOrganizations().isEmpty()) {
			source.getOrganizations().forEach(target::addOrganization);
		}
		source.getAddresses().forEach(target::addAddress);
		if (target.getStructuredName() == null && source.getStructuredName() != null) {
			target.setStructuredName(source.getStructuredName());
		}
	}

	/** More property lines = richer card = merge target. */
	private int richness(VCard vcard) {
		return vcard.getTelephoneNumbers().size() + vcard.getEmails().size() + vcard.getUrls().size()
				+ vcard.getNotes().size() + vcard.getAddresses().size() + vcard.getOrganizations().size()
				+ ((vcard.getBirthday() != null) ? 1 : 0) + ((vcard.getStructuredName() != null) ? 1 : 0);
	}

	/** Case- and order-insensitive name token key: "Muster Max" -> "muster max". */
	private String nameKey(VCard vcard) {
		String name = displayName(vcard);
		if ("<unnamed>".equals(name)) {
			return "";
		}
		Set<String> tokens = new TreeSet<>();
		for (String token : name.toLowerCase(Locale.ROOT).split("[\\s,]+")) {
			if (!token.isBlank()) {
				tokens.add(token);
			}
		}
		return (tokens.size() >= 2) ? String.join(" ", tokens) : "";
	}

	private Set<String> sharedValueKeys(VCard vcard) {
		Set<String> keys = new HashSet<>();
		vcard.getTelephoneNumbers().forEach((telephone) -> {
			if (telephone.getText() != null && !telephone.getText().isBlank()) {
				keys.add("tel:" + telephone.getText().replaceAll("[\\s\\-./()]", ""));
			}
		});
		vcard.getEmails().forEach((email) -> {
			if (email.getValue() != null && !email.getValue().isBlank()) {
				keys.add("eml:" + email.getValue().trim().toLowerCase(Locale.ROOT));
			}
		});
		return keys;
	}

	private String displayName(VCard vcard) {
		return (vcard.getFormattedName() != null && vcard.getFormattedName().getValue() != null
				&& !vcard.getFormattedName().getValue().isBlank()) ? vcard.getFormattedName().getValue() : "<unnamed>";
	}

	private int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private void union(int[] parent, int i, int j) {
		parent[find(parent, i)] = find(parent, j);
	}

}
