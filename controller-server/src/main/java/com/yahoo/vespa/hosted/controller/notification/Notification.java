// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * @author freva
 */
public class Notification {
    private final Instant at;
    private final Type type;
    private final NotificationSource source;
    private final List<String> messages;

    public Notification(Instant at, Type type, NotificationSource source, List<String> messages) {
        this.at = Objects.requireNonNull(at, "at cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.messages = List.copyOf(Objects.requireNonNull(messages, "messages cannot be null"));
        if (messages.size() < 1) throw new IllegalArgumentException("messages cannot be empty");
    }

    public Instant at() { return at; }
    public Type type() { return type; }
    public NotificationSource source() { return source; }
    public List<String> messages() { return messages; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Notification that = (Notification) o;
        return at.equals(that.at) && type == that.type && source.equals(that.source) && messages.equals(that.messages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(at, type, source, messages);
    }

    @Override
    public String toString() {
        return "Notification{" +
                "at=" + at +
                ", type=" + type +
                ", source=" + source +
                ", messages=" + messages +
                '}';
    }

    public enum Level {
        warning, error;
    }

    public enum Type {
        /** Warnings about usage of deprecated features in application package */
        APPLICATION_PACKAGE_WARNING(Level.warning),

        /** Failure to deploy application package */
        DEPLOYMENT_FAILURE(Level.error);

        private final Level level;
        Type(Level level) {
            this.level = level;
        }

        public Level level() {
            return level;
        }
    }

}
