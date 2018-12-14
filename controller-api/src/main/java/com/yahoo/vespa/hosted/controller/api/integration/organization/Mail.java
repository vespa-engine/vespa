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
    private final String message;

    public Mail(List<String> recipients, String message) {
        recipients.forEach(Objects::requireNonNull);
        this.recipients = ImmutableList.copyOf(recipients);
        this.message = Objects.requireNonNull(message);
    }

    public List<String> recipients() { return recipients; }
    public String message() { return message; }

}
