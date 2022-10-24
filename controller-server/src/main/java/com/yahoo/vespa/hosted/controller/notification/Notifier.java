// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import com.google.common.annotations.VisibleForTesting;
import com.yahoo.config.provision.Environment;
import com.yahoo.text.Text;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mail;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mailer;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MailerException;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Notifier is responsible for dispatching user notifications to their chosen Contact points.
 *
 * @author enygaard
 */
public class Notifier {
    private static final String header = """ 
                <div style="background: #00598c; height: 55px; width: 100%">
                  <img
                    src="https://vespa.ai/assets/vespa-logo.png"
                    style="width: auto; height: 34px; margin: 10px"
                  />
                </div>
                <br>
                """;

    private final CuratorDb curatorDb;
    private final Mailer mailer;
    private final FlagSource flagSource;
    private final NotificationFormatter formatter;

    private static final Logger log = Logger.getLogger(Notifier.class.getName());

    // Minimal url pattern matcher to detect hardcoded URLs in Notification messages
    private static final Pattern urlPattern = Pattern.compile("https://[\\w\\d./]+");

    public Notifier(CuratorDb curatorDb, ZoneRegistry zoneRegistry, Mailer mailer, FlagSource flagSource) {
        this.curatorDb = Objects.requireNonNull(curatorDb);
        this.mailer = Objects.requireNonNull(mailer);
        this.flagSource = Objects.requireNonNull(flagSource);
        this.formatter = new NotificationFormatter(zoneRegistry);
    }

    public void dispatch(List<Notification> notifications, NotificationSource source) {
        if (!dispatchEnabled(source) || skipSource(source)) {
            return;
        }
        if (notifications.isEmpty()) {
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
        dispatch(List.of(notification), notification.source());
    }

    private boolean dispatchEnabled(NotificationSource source) {
        return Flags.NOTIFICATION_DISPATCH_FLAG.bindTo(flagSource)
                .with(FetchVector.Dimension.TENANT_ID, source.tenant().value())
                .value();
    }

    private boolean skipSource(NotificationSource source) {
        // Do not dispatch notification for dev and perf environments
        if (source.zoneId()
                .map(z -> z.environment())
                .map(e -> e == Environment.dev || e == Environment.perf)
                .orElse(false)) {
            return true;
        }
        return false;
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
            var content = formatter.format(notification);
            mailer.send(mailOf(content, contacts.stream()
                    .filter(c -> c.email().isVerified())
                    .map(c -> c.email().getEmailAddress())
                    .collect(Collectors.toList())));
        } catch (MailerException e) {
            log.log(Level.SEVERE, "Failed sending email", e);
        } catch (MissingOptionalException e) {
            log.log(Level.WARNING, "Missing value in required field '" + e.field() + "' for notification type: " + notification.type(), e);
        }
    }

    public Mail mailOf(FormattedNotification content, Collection<String> recipients) {
        var notification = content.notification();
        var subject = Text.format("[%s] %s Vespa Notification for %s", notification.level().toString().toUpperCase(), content.prettyType(), applicationIdSource(notification.source()));
        String body = new StringBuilder()
                .append(content.messagePrefix()).append("\n")
                .append(notification.messages().stream().map(m -> " * " + m).collect(Collectors.joining("\n"))).append("\n")
                .append("\n")
                .append("Vespa Console link:\n")
                .append(content.uri().toString()).toString();
        String html = new StringBuilder()
                .append(header)
                .append(content.messagePrefix()).append("<br>\n")
                .append("<ul>\n")
                .append(notification.messages().stream()
                        .map(Notifier::linkify)
                        .map(m -> "<li>" + m + "</li>")
                        .collect(Collectors.joining("<br>\n")))
                .append("</ul>\n")
                .append("<br>\n")
                .append("<a href=\"" + content.uri() + "\">Vespa Console</a>").toString();
        return new Mail(recipients, subject, body, html);
    }

    @VisibleForTesting
    static String linkify(String text) {
        return urlPattern.matcher(text).replaceAll((res) -> String.format("<a href=\"%s\">%s</a>", res.group(), res.group()));
    }

    private String applicationIdSource(NotificationSource source) {
        StringBuilder sb = new StringBuilder();
        sb.append(source.tenant().value());
        source.application().ifPresent(applicationName -> sb.append(".").append(applicationName.value()));
        source.instance().ifPresent(instanceName -> sb.append(".").append(instanceName.value()));
        return sb.toString();
    }


}
