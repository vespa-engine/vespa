// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import com.google.common.annotations.VisibleForTesting;
import com.yahoo.config.provision.Environment;
import com.yahoo.restapi.UriBuilder;
import com.yahoo.text.Text;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mail;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mailer;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MailerException;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;

import java.net.URI;
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
    private final CuratorDb curatorDb;
    private final Mailer mailer;
    private final FlagSource flagSource;
    private final NotificationFormatter formatter;
    private final URI dashboardUri;
    private final MailTemplating mailTemplating;

    private static final Logger log = Logger.getLogger(Notifier.class.getName());

    // Minimal url pattern matcher to detect hardcoded URLs in Notification messages
    private static final Pattern urlPattern = Pattern.compile("https://[\\w\\d./]+");

    public Notifier(CuratorDb curatorDb, ZoneRegistry zoneRegistry, Mailer mailer, FlagSource flagSource) {
        this.curatorDb = Objects.requireNonNull(curatorDb);
        this.mailer = Objects.requireNonNull(mailer);
        this.flagSource = Objects.requireNonNull(flagSource);
        this.formatter = new NotificationFormatter(zoneRegistry);
        this.dashboardUri = zoneRegistry.dashboardUrl();
        this.mailTemplating = new MailTemplating(zoneRegistry);
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
            if (t instanceof CloudTenant ct) {
                ct.info().contacts().all().stream()
                        .filter(c -> c.audiences().contains(TenantContacts.Audience.NOTIFICATIONS))
                        .collect(Collectors.groupingBy(TenantContacts.Contact::type, Collectors.toList()))
                        .forEach((type, contacts) -> notifications.forEach(n -> dispatch(n, type, contacts)));
            }
        });
    }

    public void dispatch(Notification notification) {
        dispatch(List.of(notification), notification.source());
    }

    private boolean dispatchEnabled(NotificationSource source) {
        return PermanentFlags.NOTIFICATION_DISPATCH_FLAG.bindTo(flagSource)
                .with(FetchVector.Dimension.TENANT_ID, source.tenant().value())
                .value();
    }

    private boolean skipSource(NotificationSource source) {
        // Do not dispatch notification for dev and perf environments
        return source.zoneId()
                .map(z -> z.environment())
                .map(e -> e == Environment.dev || e == Environment.perf)
                .orElse(false);
    }

    private void dispatch(Notification notification, TenantContacts.Type type, Collection<? extends TenantContacts.Contact> contacts) {
        switch (type) {
            case EMAIL -> dispatch(notification, contacts.stream().map(c -> (TenantContacts.EmailContact) c).toList());
            default -> throw new IllegalArgumentException("Unknown TenantContacts type " + type.name());
        }
    }

    private void dispatch(Notification notification, Collection<TenantContacts.EmailContact> contacts) {
        try {
            log.fine(() -> "Sending notification " + notification + " to " +
                    contacts.stream().map(c -> c.email().getEmailAddress()).toList());
            var content = formatter.format(notification);
            mailer.send(mailOf(content, contacts.stream()
                    .filter(c -> c.email().isVerified())
                    .map(c -> c.email().getEmailAddress())
                    .toList()));
        } catch (MailerException e) {
            log.log(Level.SEVERE, "Failed sending email", e);
        } catch (MissingOptionalException e) {
            log.log(Level.WARNING, "Missing value in required field '" + e.field() + "' for notification type: " + notification.type(), e);
        }
    }

    public Mail mailOf(FormattedNotification content, Collection<String> recipients) {
        var notification = content.notification();
        var subject = content.notification().mailContent().flatMap(Notification.MailContent::subject)
                .orElseGet(() -> Text.format(
                        "[%s] %s Vespa Notification for %s", notification.level().toString().toUpperCase(),
                        content.prettyType(), applicationIdSource(notification.source())));
        var html = generateHtml(content);
        return new Mail(recipients, subject, "", html);
    }

    private String generateHtml(FormattedNotification content) {
        var mailContent = content.notification().mailContent().orElseGet(() -> generateContentFromMessages(content));
        return mailTemplating.generateDefaultMailHtml(mailContent.template(), mailContent.values(), content.notification().source().tenant());
    }

    private Notification.MailContent generateContentFromMessages(FormattedNotification f) {
        var items = f.notification().messages().stream().map(m -> capitalise(linkify(mailTemplating.escapeHtml(m)))).toList();
        return Notification.MailContent.fromTemplate(MailTemplating.Template.DEFAULT_MAIL_CONTENT)
                .with("mailMessageTemplate", "notification-message")
                .with("mailTitle", "Vespa Cloud Notifications")
                .with("notificationHeader", f.messagePrefix())
                .with("notificationItems", items)
                .with("consoleLink", notificationLink(f.notification().source()))
                .build();
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

    private String notificationLink(NotificationSource source) {
        var uri = new UriBuilder(dashboardUri);
        uri = uri.append("tenant").append(source.tenant().value());
        if (source.application().isPresent())
            uri = uri.append("application").append(source.application().get().value());
        if (source.isProduction()) {
            uri = uri.append("prod/instance");
            if (source.jobType().isPresent()) {
                uri = uri.append(source.instance().get().value());
            }
        }
        else {
            uri = uri.append("dev/instance/").append(source.instance().get().value());
        }
        if (source.jobType().isPresent()) {
            uri = uri.append("job").append(source.jobType().get().jobName()).append("run").append(String.valueOf(source.runNumber().getAsLong()));
        }
        return uri.toString();
    }

    private static String capitalise(String m) {
        return m.substring(0, 1).toUpperCase() + m.substring(1);
    }
}
