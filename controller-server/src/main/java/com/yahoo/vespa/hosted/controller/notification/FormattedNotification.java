package com.yahoo.vespa.hosted.controller.notification;

import java.net.URI;
import java.util.Objects;

/**
 * Contains formatted text that can be displayed to a user to give extra information and pointers for a given
 * Notification.
 *
 * @author enygaard
 */
public record FormattedNotification(Notification notification, String prettyType, String messagePrefix, URI uri) {

    public FormattedNotification {
        Objects.requireNonNull(prettyType);
        Objects.requireNonNull(messagePrefix);
        Objects.requireNonNull(uri);
        Objects.requireNonNull(notification);
    }

}
