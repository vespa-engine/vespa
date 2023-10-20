// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Represents an event that we want to notify the tenant about. The message(s) should be short
 * and only describe event details: the final presentation will prefix the message with general
 * information from other metadata in this notification (e.g. links to relevant console views
 * and/or relevant documentation.
 *
 * @author freva
 */
public record Notification(Instant at, Notification.Type type, Notification.Level level, NotificationSource source,
                           String title, List<String> messages, Optional<MailContent> mailContent) {

    public Notification(Instant at, Type type, Level level, NotificationSource source, String title, List<String> messages) {
        this(at, type, level, source, title, messages, Optional.empty());
    }

    public Notification(Instant at, Type type, Level level, NotificationSource source, List<String> messages) {
        this(at, type, level, source, "", messages);
    }

    public Notification {
        at = Objects.requireNonNull(at, "at cannot be null");
        type = Objects.requireNonNull(type, "type cannot be null");
        level = Objects.requireNonNull(level, "level cannot be null");
        source = Objects.requireNonNull(source, "source cannot be null");
        title = Objects.requireNonNull(title, "title cannot be null");
        messages = List.copyOf(Objects.requireNonNull(messages, "messages cannot be null"));

        // Allowing empty title temporarily until all notifications have a title
        // if (title.isBlank()) throw new IllegalArgumentException("title cannot be empty");
        if (messages.isEmpty() && title.isBlank()) throw new IllegalArgumentException("messages cannot be empty when title is empty");

        mailContent = Objects.requireNonNull(mailContent);
    }

    public enum Level {
        // Must be ordered in order of importance
        info, warning, error
    }

    public enum Type {

        /**
         * Related to contents of application package, e.g., usage of deprecated features/syntax
         */
        applicationPackage,

        /**
         * Related to contents of application package detectable by the controller on submission
         */
        submission,

        /**
         * Related to contents of application test package, e.g., mismatch between deployment spec and provided tests
         */
        testPackage,

        /**
         * Related to deployment of application, e.g., system test failure, node allocation failure, internal errors, etc.
         */
        deployment,

        /**
         * Application cluster is (near) external feed blocked
         */
        feedBlock,

        /**
         * Application cluster is reindexing document(s)
         */
        reindex,

        /**
         * Account, e.g. expiration of trial plan
         */
        account
    }

    public static class MailContent {
        private final String template;
        private final SortedMap<String, Object> values;
        private final String subject;

        private MailContent(Builder b) {
            template = Objects.requireNonNull(b.template);
            values = new TreeMap<>(b.values);
            subject = b.subject;
        }

        public String template() { return template; }
        public SortedMap<String, Object> values() { return Collections.unmodifiableSortedMap(values); }
        public Optional<String> subject() { return Optional.ofNullable(subject); }

        public static Builder fromTemplate(String template) { return new Builder(template); }

        public static class Builder {
            private final String template;
            private final Map<String, Object> values = new HashMap<>();
            private String subject;

            private Builder(String template) {
                this.template = template;
            }

            public Builder with(String name, String value) { values.put(name, value); return this; }
            public Builder with(String name, Collection<String> items) { values.put(name, List.copyOf(items)); return this; }
            public Builder subject(String s) { this.subject = s; return this; }
            public MailContent build() { return new MailContent(this); }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MailContent that = (MailContent) o;
            return Objects.equals(template, that.template) && Objects.equals(values, that.values) && Objects.equals(subject, that.subject);
        }

        @Override
        public int hashCode() {
            return Objects.hash(template, values, subject);
        }

        @Override
        public String toString() {
            return "MailContent{" +
                    "template='" + template + '\'' +
                    ", values=" + values +
                    ", subject='" + subject + '\'' +
                    '}';
        }
    }

}
