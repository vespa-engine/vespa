// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;

/**
 * A message with a subject and a nonempty set of recipients.
 *
 * @author jonmv
 */
public class Mail {

    private final Collection<String> recipients;
    private final String subject;
    private final String message;
    private final Optional<String> htmlMessage;

    public Mail(Collection<String> recipients, String subject, String message) {
        this(recipients, subject, message, Optional.empty());
    }

    public Mail(Collection<String> recipients, String subject, String message, String htmlMessage) {
        this(recipients, subject, message, Optional.of(htmlMessage));
    }

    Mail(Collection<String> recipients, String subject, String message, Optional<String> htmlMessage) {
        if (recipients.isEmpty())
            throw new IllegalArgumentException("Empty recipient list is not allowed.");
        recipients.forEach(Objects::requireNonNull);
        this.recipients = ImmutableList.copyOf(recipients);
        this.subject = Objects.requireNonNull(subject);
        this.message = Objects.requireNonNull(message);
        this.htmlMessage = Objects.requireNonNull(htmlMessage);
    }

    public Collection<String> recipients() { return recipients; }
    public String subject() { return subject; }
    public String message() { return message; }
    public Optional<String> htmlMessage() { return htmlMessage; }

}
