package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.google.common.collect.ImmutableList;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A message based on a configured template
 *
 * @author enygaard
 */
public class TemplatedMail implements Mail {

    private final Collection<String> recipients;
    private final String templateName;
    private final Map<String, String> input;

    public TemplatedMail(Collection<String> recipients, String templateName, Map<String, String> input) {
        if (recipients.isEmpty())
            throw new IllegalArgumentException("Empty recipient list is not allowed.");
        recipients.forEach(Objects::requireNonNull);
        this.recipients = ImmutableList.copyOf(recipients);
        this.templateName = Objects.requireNonNull(templateName);
        if (input == null) {
            input = new HashMap<>();
        }
        input.keySet().forEach(Objects::requireNonNull);
        input.values().forEach(Objects::requireNonNull);
        this.input = input;
    }

    public Collection<String> recipients() {
        return recipients;
    }

    public String templateName() {
        return templateName;
    }

    public Map<String, String> input() {
        return input;
    }
}
