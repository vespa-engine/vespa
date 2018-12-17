package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.google.common.collect.ImmutableList;

import java.util.List;
import java.util.Objects;

/**
 * A message with a sender and a set of recipients.
 *
 * @author jonmv
 */
public class Mail {

    private final List<String> recipients;
    private final String subject;
    private final String message;

    public Mail(List<String> recipients, String subject, String message) {
        if (recipients.isEmpty())
            throw new IllegalArgumentException("Empty recipient list is not allowed.");
        recipients.forEach(Objects::requireNonNull);
        this.recipients = ImmutableList.copyOf(recipients);
        this.subject = Objects.requireNonNull(subject);
        this.message = Objects.requireNonNull(message);
    }

    public List<String> recipients() { return recipients; }
    public String subject() { return subject; }
    public String message() { return message; }

}
