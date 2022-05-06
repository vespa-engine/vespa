package com.yahoo.vespa.hosted.controller.notification;

import java.net.URI;
import java.util.Objects;

/**
 * Contains formatted text that can be displayed to a user to give extra information and pointers for a given
 * Notification.
 *
 * @author enygaard
 */
public class FormattedNotification {
    private final String prettyType;
    private final String messagePrefix;
    private final URI uri;
    private final Notification notification;

    public FormattedNotification(Notification notification, String prettyType, String messagePrefix, URI uri) {
        this.prettyType = Objects.requireNonNull(prettyType);
        this.messagePrefix = Objects.requireNonNull(messagePrefix);
        this.uri = Objects.requireNonNull(uri);
        this.notification = Objects.requireNonNull(notification);
    }

    public String prettyType() {
        return prettyType;
    }

    public String messagePrefix() {
        return messagePrefix;
    }

    public URI uri() {
        return uri;
    }

    public Notification notification() {
        return notification;
    }
}