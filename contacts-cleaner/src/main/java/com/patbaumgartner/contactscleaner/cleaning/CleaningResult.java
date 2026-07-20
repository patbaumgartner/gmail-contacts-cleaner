package com.patbaumgartner.contactscleaner.cleaning;

/**
 * Outcome of cleaning a single vCard.
 *
 * @param changed whether any cleaning rule modified the vCard — only changed contacts
 * need to be written back to the server
 * @param empty whether the contact has neither a phone number nor an e-mail address and
 * is therefore a deletion candidate (acted upon only when
 * {@code contacts-cleaner.cleaning.delete-empty-contacts=true})
 */
public record CleaningResult(boolean changed, boolean empty) {
}
