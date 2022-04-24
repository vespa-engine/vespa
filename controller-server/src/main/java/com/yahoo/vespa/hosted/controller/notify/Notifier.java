// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notify;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mail;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mailer;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MailerException;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Notifier is responsible for dispatching user notifications to their chosen Contact points.
 *
 * @author enygaard
 */
public class Notifier {
    private final CuratorDb curatorDb;
    private final ZoneRegistry zoneRegistry;
    private final Mailer mailer;

    private static final Logger log = Logger.getLogger(Notifier.class.getName());

    public Notifier(CuratorDb curatorDb, ZoneRegistry zoneRegistry, Mailer mailer) {
        this.curatorDb = Objects.requireNonNull(curatorDb);
        this.zoneRegistry = Objects.requireNonNull(zoneRegistry);
        this.mailer = Objects.requireNonNull(mailer);
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

    private boolean skipSource(NotificationSource source) {
        // Limit sources to production systems only. Dev and test systems cause too much noise at the moment.
        if (source.zoneId().map(z -> z.environment() != Environment.prod).orElse(false)) {
            return true;
        } else if (source.jobType().map(t -> !t.isProduction()).orElse(false)) {
            return true;
        }
        return false;
    }

    public void dispatch(Notification notification) {
        dispatch(List.of(notification), notification.source());
    }

    private void dispatch(Notification notification, TenantContacts.Type type, Collection<? extends TenantContacts.Contact> contacts) {
        switch (type) {
            case EMAIL:
                dispatch(notification, contacts.stream().map(c -> (TenantContacts.EmailContact) c).collect(Collectors.toList()));
                break;
            default:
                throw new IllegalArgumentException("Unknown TenantContacts type " + type.name());
        }
    }

    private void dispatch(Notification notification, Collection<TenantContacts.EmailContact> contacts) {
        try {
            mailer.send(mailOf(notification, contacts.stream().map(c -> c.email()).collect(Collectors.toList())));
        } catch (MailerException e) {
            log.log(Level.SEVERE, "Failed sending email", e);
        }
    }

    private Mail mailOf(Notification n, Collection<String> recipients) {
        var source = n.source();
        var subject = Text.format("[%s] %s Vespa Notification for %s", n.level().toString().toUpperCase(), n.type().name(), applicationIdSource(source));
        var body = new StringBuilder();
        body.append("Source: ").append(n.source().toString()).append("\n")
                .append("\n")
                .append(String.join("\n", n.messages()))
                .append("\n")
                .append(url(source).toString());
        return new Mail(recipients, subject, body.toString());
    }

    private String applicationIdSource(NotificationSource source) {
        StringBuilder sb = new StringBuilder();
        sb.append(source.tenant().value());
        source.application().ifPresent(applicationName -> sb.append(".").append(applicationName.value()));
        source.instance().ifPresent(instanceName -> sb.append(".").append(instanceName.value()));
        return sb.toString();
    }

    private URI url(NotificationSource source) {
        if (source.application().isPresent() && source.instance().isPresent()) {
            if (source.jobType().isPresent() && source.runNumber().isPresent()) {
                return zoneRegistry.dashboardUrl(
                        new RunId(ApplicationId.from(source.tenant(),
                                source.application().get(),
                                source.instance().get()),
                                source.jobType().get(),
                                source.runNumber().getAsLong()));
            }
            return zoneRegistry.dashboardUrl(ApplicationId.from(source.tenant(), source.application().get(), source.instance().get()));
        }
        return zoneRegistry.dashboardUrl(source.tenant());
    }

}
