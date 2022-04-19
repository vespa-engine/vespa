// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notify;

import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.hosted.controller.api.integration.notification.Notification;
import com.yahoo.vespa.hosted.controller.api.integration.notification.NotificationSource;
import com.yahoo.vespa.hosted.controller.api.integration.notify.NotifyDispatcher;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Notifier is responsible for dispatching user notifications to their chosen Contact points.
 *
 * @author enygaard
 */
public class Notifier {
    private final CuratorDb curatorDb;
    private final NotifyDispatcher dispatcher;
    public Notifier(CuratorDb curatorDb, NotifyDispatcher dispatcher) {
        this.curatorDb = Objects.requireNonNull(curatorDb);
        this.dispatcher = Objects.requireNonNull(dispatcher);
    }

    public void dispatch(List<Notification> notifications, NotificationSource source) {
        if (notifications.isEmpty()) {
            return;
        }
        if (skipSource(source)) {
            return;
        }
        var tenant = curatorDb.readTenant(source.tenant());
        tenant.stream().forEach(t -> {
            if (t instanceof CloudTenant) {
                var ct = (CloudTenant) t;
                ct.info().contacts().all().stream()
                        .filter(c -> c.audiences().contains(TenantContacts.Audience.NOTIFICATIONS))
                        .collect(Collectors.groupingBy(TenantContacts.Contact::type, Collectors.toList()))
                        .entrySet()
                        .forEach(e -> notifications.forEach(n -> dispatch(n, e.getKey(), e.getValue())));
            }
        });
    }

    public void dispatch(Notification notification) {
        this.dispatch(List.of(notification), notification.source());
    }

    private boolean skipSource(NotificationSource source) {
        // Limit sources to production systems only. Dev and test systems cause too much noise at the moment.
        if (source.zoneId().map(z -> z.environment() != Environment.prod).orElse(false)) {
            return true;
        } else if (source.jobType().map(t -> !t.isProduction()).orElse(false)) {
            return true;
        }
        return false;
    }

    private void dispatch(Notification notification, TenantContacts.Type type, Collection<? extends TenantContacts.Contact> contacts) {
        switch (type) {
            case EMAIL:
                dispatcher.mail(notification, contacts.stream().map(c -> (TenantContacts.EmailContact) c).collect(Collectors.toList()));
                break;
            default:
                throw new IllegalArgumentException("Unknown TenantContacts type " + type.name());
        }
    }
}
