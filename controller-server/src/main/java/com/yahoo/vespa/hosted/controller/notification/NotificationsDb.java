// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adds, updates and removes tenant notifications in ZK
 *
 * @author freva
 */
public class NotificationsDb {

    private final Clock clock;
    private final CuratorDb curatorDb;

    public NotificationsDb(Controller controller) {
        this(controller.clock(), controller.curator());
    }

    NotificationsDb(Clock clock, CuratorDb curatorDb) {
        this.clock = clock;
        this.curatorDb = curatorDb;
    }

    public List<Notification> listNotifications(NotificationSource source, boolean productionOnly) {
        return curatorDb.readNotifications(source.tenant()).stream()
                .filter(notification -> source.contains(notification.source()) && (!productionOnly || notification.source().isProduction()))
                .collect(Collectors.toUnmodifiableList());
    }

    public void setNotification(NotificationSource source, Notification.Type type, String message) {
        setNotification(source, type, List.of(message));
    }

    /**
     * Add a notification with given source and type. If a notification with same source and type
     * already exists, it'll be replaced by this one instead
     */
    public void setNotification(NotificationSource source, Notification.Type type, List<String> messages) {
        try (Lock lock = curatorDb.lockNotifications(source.tenant())) {
            List<Notification> notifications = curatorDb.readNotifications(source.tenant()).stream()
                    .filter(notification -> !source.equals(notification.source()) || type != notification.type())
                    .collect(Collectors.toCollection(ArrayList::new));
            notifications.add(new Notification(clock.instant(), type, source, messages));
            curatorDb.writeNotifications(source.tenant(), notifications);
        }
    }

    /** Remove the notification with the given source and type */
    public void removeNotification(NotificationSource source, Notification.Type type) {
        try (Lock lock = curatorDb.lockNotifications(source.tenant())) {
            List<Notification> initial = curatorDb.readNotifications(source.tenant());
            List<Notification> filtered = initial.stream()
                    .filter(notification -> !source.equals(notification.source()) || type != notification.type())
                    .collect(Collectors.toUnmodifiableList());
            if (initial.size() > filtered.size())
                curatorDb.writeNotifications(source.tenant(), filtered);
        }
    }

    /** Remove all notifications for this source or sources contained by this source */
    public void removeNotifications(NotificationSource source) {
        try (Lock lock = curatorDb.lockNotifications(source.tenant())) {
            if (source.application().isEmpty()) { // Source is tenant
                curatorDb.deleteNotifications(source.tenant());
                return;
            }

            List<Notification> initial = curatorDb.readNotifications(source.tenant());
            List<Notification> filtered = initial.stream()
                    .filter(notification -> !source.contains(notification.source()))
                    .collect(Collectors.toUnmodifiableList());
            if (initial.size() > filtered.size())
                curatorDb.writeNotifications(source.tenant(), filtered);
        }
    }
}
