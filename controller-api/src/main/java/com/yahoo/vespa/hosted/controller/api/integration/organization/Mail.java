// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * A message with a subject and a nonempty set of recipients.
 *
 * @author jonmv
 */
public class Mail {

    private final Collection<String> recipients;
    private final String subject;
    private final String message;

    public Mail(Collection<String> recipients, String subject, String message) {
        if (recipients.isEmpty())
            throw new IllegalArgumentException("Empty recipient list is not allowed.");
        recipients.forEach(Objects::requireNonNull);
        this.recipients = ImmutableList.copyOf(recipients);
        this.subject = Objects.requireNonNull(subject);
        this.message = Objects.requireNonNull(message);
    }

    public Collection<String> recipients() { return recipients; }
    public String subject() { return subject; }
    public String message() { return message; }

}
