// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import com.google.common.annotations.VisibleForTesting;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.text.Text;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.api.integration.ConsoleUrls;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mail;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Mailer;
import com.yahoo.vespa.hosted.controller.api.integration.organization.MailerException;
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
    private final CuratorDb curatorDb;
    private final Mailer mailer;
    private final FlagSource flagSource;
    private final ConsoleUrls consoleUrls;
    private final NotificationFormatter formatter;
    private final MailTemplating mailTemplating;

    private static final Logger log = Logger.getLogger(Notifier.class.getName());

    // Minimal url pattern matcher to detect hardcoded URLs in Notification messages
    private static final Pattern urlPattern = Pattern.compile("https://[\\w\\d./]+");

    public Notifier(CuratorDb curatorDb, ConsoleUrls consoleUrls, Mailer mailer, FlagSource flagSource) {
        this.curatorDb = Objects.requireNonNull(curatorDb);
        this.mailer = Objects.requireNonNull(mailer);
        this.flagSource = Objects.requireNonNull(flagSource);
        this.consoleUrls = Objects.requireNonNull(consoleUrls);
        this.formatter = new NotificationFormatter(consoleUrls);
        this.mailTemplating = new MailTemplating(consoleUrls);
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
            var verifiedContacts = contacts.stream()
                    .filter(c -> c.email().isVerified()).map(c -> c.email().getEmailAddress()).toList();
            if (verifiedContacts.isEmpty()) {
                log.fine(() -> "None of the %d contact(s) are verified - skipping delivery of %s".formatted(contacts.size(), notification));
                return;
            }
            mailer.send(mailOf(content, verifiedContacts));
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
                .with("consoleLink", notificationLink(consoleUrls, f.notification().source()))
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

    static String notificationLink(ConsoleUrls consoleUrls, NotificationSource source) {
        if (source.application().isEmpty()) return consoleUrls.tenantOverview(source.tenant());
        if (source.instance().isEmpty()) return consoleUrls.prodApplicationOverview(source.tenant(), source.application().get());

        ApplicationId application = ApplicationId.from(source.tenant(), source.application().get(), source.instance().get());
        if (source.jobType().isPresent())
            return consoleUrls.deploymentRun(new RunId(application, source.jobType().get(), source.runNumber().getAsLong()));
        if (source.clusterId().isPresent())
            return consoleUrls.clusterOverview(application, source.zoneId().get(), source.clusterId().get());
        return consoleUrls.instanceOverview(application, source.zoneId().map(ZoneId::environment).orElse(Environment.prod));
    }

    private static String capitalise(String m) {
        return m.substring(0, 1).toUpperCase() + m.substring(1);
    }
}
