// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Represents an event that we want to notify the tenant about. The message(s) should be short
 * and only describe event details: the final presentation will prefix the message with general
 * information from other metadata in this notification (e.g. links to relevant console views
 * and/or relevant documentation.
 *
 * @author freva
 */
public record Notification(Instant at, com.yahoo.vespa.hosted.controller.notification.Notification.Type type, com.yahoo.vespa.hosted.controller.notification.Notification.Level level, NotificationSource source, List<String> messages) {

    public Notification(Instant at, Type type, Level level, NotificationSource source, List<String> messages) {
        this.at = Objects.requireNonNull(at, "at cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.level = Objects.requireNonNull(level, "level cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages cannot be null"));
        if (messages.size() < 1) throw new IllegalArgumentException("messages cannot be empty");
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
        reindex

    }

}
