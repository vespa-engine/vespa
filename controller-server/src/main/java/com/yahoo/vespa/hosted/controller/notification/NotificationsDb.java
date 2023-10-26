// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import com.yahoo.collections.Pair;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.TenantName;
import com.yahoo.text.Text;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.ConsoleUrls;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.notification.Notification.MailContent;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing.Cluster;
import static com.yahoo.vespa.hosted.controller.notification.Notification.Level;
import static com.yahoo.vespa.hosted.controller.notification.Notification.Type;
import static com.yahoo.vespa.hosted.controller.notification.Notifier.notificationLink;

/**
 * Adds, updates and removes tenant notifications in ZK
 *
 * @author freva
 */
public class NotificationsDb {

    private static final Logger log = Logger.getLogger(NotificationsDb.class.getName());

    private final Clock clock;
    private final CuratorDb curatorDb;
    private final Notifier notifier;
    private final ConsoleUrls consoleUrls;

    public NotificationsDb(Controller controller) {
        this(controller.clock(), controller.curator(), controller.notifier(), controller.serviceRegistry().consoleUrls());
    }

    NotificationsDb(Clock clock, CuratorDb curatorDb, Notifier notifier, ConsoleUrls consoleUrls) {
        this.clock = clock;
        this.curatorDb = curatorDb;
        this.notifier = notifier;
        this.consoleUrls = consoleUrls;
    }

    public List<TenantName> listTenantsWithNotifications() {
        return curatorDb.listTenantsWithNotifications();
    }

    public List<Notification> listNotifications(NotificationSource source, boolean productionOnly) {
        return curatorDb.readNotifications(source.tenant()).stream()
                .filter(notification -> source.contains(notification.source()) && (!productionOnly || notification.source().isProduction()))
                .toList();
    }

    public void setSubmissionNotification(TenantAndApplicationId tenantApp, String message) {
        NotificationSource source = NotificationSource.from(tenantApp);
        String title = "Application package for [%s](%s) has a warning".formatted(
                tenantApp.application().value(), notificationLink(consoleUrls, source));
        setNotification(source, Type.submission, Level.warning, title, List.of(message), Optional.empty());
    }

    public void setApplicationPackageNotification(NotificationSource source, List<String> messages) {
        String title = "Application package for [%s%s](%s) has %s".formatted(
                source.application().get().value(), source.instance().map(i -> "." + i.value()).orElse(""), notificationLink(consoleUrls, source),
                messages.size() == 1 ? "a warning" : "warnings");
        setNotification(source, Type.applicationPackage, Level.warning, title, messages, Optional.empty());
    }

    public void setTestPackageNotification(TenantAndApplicationId tenantApp, List<String> messages) {
        NotificationSource source = NotificationSource.from(tenantApp);
        String title = "There %s with tests for [%s](%s)".formatted(
                messages.size() == 1 ? "is a problem" : "are problems", tenantApp.application().value(),
                notificationLink(consoleUrls, source));
        setNotification(source, Type.testPackage, Level.warning, title, messages, Optional.empty());
    }

    public void setDeploymentNotification(RunId runId, String message) {
        String description, linkText;
        if (runId.type().isProduction()) {
            description = runId.type().isTest() ? "Test job " : "Deployment job ";
            linkText = "#" + runId.number() + " to " + runId.type().zone().region().value();
        } else if (runId.type().isTest()) {
            description = "";
            linkText = (runId.type().isStagingTest() ? "Staging" : "System") + " test #" + runId.number();
        } else if (runId.type().isDeployment()) {
            description = "Deployment job ";
            linkText = "#" + runId.number() + " to " + runId.type().zone().value();
        } else throw new IllegalStateException("Unexpected job type " + runId.type());
        NotificationSource source = NotificationSource.from(runId);
        String title = "%s[%s](%s) for application **%s.%s** has failed".formatted(
                description, linkText, notificationLink(consoleUrls, source), runId.application().application().value(), runId.application().instance().value());
        setNotification(source, Type.deployment, Level.error, title, List.of(message), Optional.empty());
    }

    /**
     * Add a notification with given source and type. If a notification with same source and type
     * already exists, it'll be replaced by this one instead.
     */
    public void setNotification(NotificationSource source, Type type, Level level, String title, List<String> messages,
                                Optional<MailContent> mailContent) {
        Optional<Notification> changed = Optional.empty();
        try (Mutex lock = curatorDb.lockNotifications(source.tenant())) {
            var existingNotifications = curatorDb.readNotifications(source.tenant());
            List<Notification> notifications = existingNotifications.stream()
                    .filter(notification -> !source.equals(notification.source()) || type != notification.type())
                    .collect(Collectors.toCollection(ArrayList::new));
            var notification = new Notification(clock.instant(), type, level, source, title, messages, mailContent);
            if (!notificationExists(notification, existingNotifications, false)) {
                changed = Optional.of(notification);
            }
            notifications.add(notification);
            curatorDb.writeNotifications(source.tenant(), notifications);
        }
        changed.ifPresent(c -> {
            log.fine(() -> "New notification %s".formatted(c));
            notifier.dispatch(c);
        });
    }

    /** Remove the notification with the given source and type */
    public void removeNotification(NotificationSource source, Type type) {
        try (Mutex lock = curatorDb.lockNotifications(source.tenant())) {
            List<Notification> initial = curatorDb.readNotifications(source.tenant());
            List<Notification> filtered = initial.stream()
                    .filter(notification -> !source.equals(notification.source()) || type != notification.type())
                    .toList();
            if (initial.size() > filtered.size())
                curatorDb.writeNotifications(source.tenant(), filtered);
        }
    }

    /** Remove all notifications for this source or sources contained by this source */
    public void removeNotifications(NotificationSource source) {
        try (Mutex lock = curatorDb.lockNotifications(source.tenant())) {
            if (source.application().isEmpty()) { // Source is tenant
                curatorDb.deleteNotifications(source.tenant());
                return;
            }

            List<Notification> initial = curatorDb.readNotifications(source.tenant());
            List<Notification> filtered = initial.stream()
                    .filter(notification -> !source.contains(notification.source()))
                    .toList();
            if (initial.size() > filtered.size())
                curatorDb.writeNotifications(source.tenant(), filtered);
        }
    }

    /**
     * Updates notifications based on deployment metrics (e.g. feed blocked and reindexing progress) for the given
     * deployment based on current cluster metrics.
     * Will clear notifications of any cluster not reporting the metrics or whose metrics indicate feed is not blocked
     * or reindexing no longer in progress. Will set notification for clusters:
     *  - that are (Level.error) or are nearly (Level.warning) feed blocked,
     *  - that are (Level.info) currently reindexing at least 1 document type.
     */
    public void setDeploymentMetricsNotifications(DeploymentId deploymentId, List<ClusterMetrics> clusterMetrics, ApplicationReindexing applicationReindexing) {
        Instant now = clock.instant();
        List<Notification> changed = List.of();
        List<Notification> newNotifications = Stream.concat(
                clusterMetrics.stream().map(metric -> createFeedBlockNotification(consoleUrls, deploymentId, metric.getClusterId(), now, metric)),
                applicationReindexing.clusters().entrySet().stream().map(entry ->
                        createReindexNotification(consoleUrls, deploymentId, entry.getKey(), now, entry.getValue())))
                .flatMap(Optional::stream)
                .toList();

        NotificationSource deploymentSource = NotificationSource.from(deploymentId);
        try (Mutex lock = curatorDb.lockNotifications(deploymentSource.tenant())) {
            List<Notification> initial = curatorDb.readNotifications(deploymentSource.tenant());
            List<Notification> updated = Stream.concat(
                    initial.stream()
                            .filter(notification ->
                                    // Filter out old feed block notifications and reindex for this deployment
                                    (notification.type() != Type.feedBlock && notification.type() != Type.reindex) ||
                                            !deploymentSource.contains(notification.source())),
                    // ... and add the new notifications for this deployment
                    newNotifications.stream())
                    .toList();
            if (!initial.equals(updated)) {
                curatorDb.writeNotifications(deploymentSource.tenant(), updated);
            }
            changed = newNotifications.stream().filter(n -> !notificationExists(n, initial, true)).toList();
        }
        notifier.dispatch(changed, deploymentSource);
    }

    private boolean notificationExists(Notification notification, List<Notification> existing, boolean mindHigherLevel) {
        // Be conservative for now, only dispatch notifications if they are from new source or with new type.
        // the message content and level is ignored for now
        boolean exists = existing.stream()
                .anyMatch(e -> notification.source().contains(e.source()) && notification.type().equals(e.type()) &&
                        (!mindHigherLevel || notification.level().ordinal() <= e.level().ordinal()));
        log.fine(() -> "%s in %s == %b".formatted(notification, existing, exists));
        return exists;
    }

    private static Optional<Notification> createFeedBlockNotification(ConsoleUrls consoleUrls, DeploymentId deployment, String clusterId, Instant at, ClusterMetrics metric) {
        Optional<Pair<Level, String>> memoryStatus =
                resourceUtilToFeedBlockStatus("memory", metric.memoryUtil(), metric.memoryFeedBlockLimit());
        Optional<Pair<Level, String>> diskStatus =
                resourceUtilToFeedBlockStatus("disk", metric.diskUtil(), metric.diskFeedBlockLimit());
        if (memoryStatus.isEmpty() && diskStatus.isEmpty()) return Optional.empty();

        NotificationSource source = NotificationSource.from(deployment, ClusterSpec.Id.from(clusterId));
        // Find the max among levels
        Level level = Stream.of(memoryStatus, diskStatus)
                .flatMap(status -> status.stream().map(Pair::getFirst))
                .max(Comparator.comparing(Enum::ordinal)).get();
        String title = "Cluster [%s](%s) in **%s** for **%s.%s** is %sfeed blocked".formatted(
                clusterId, notificationLink(consoleUrls, source), deployment.zoneId().value(), deployment.applicationId().application().value(),
                deployment.applicationId().instance().value(), level == Level.warning ? "nearly " : "");
        List<String> messages = Stream.concat(memoryStatus.stream(), diskStatus.stream())
                .filter(status -> status.getFirst() == level) // Do not mix message from different levels
                .map(Pair::getSecond)
                .toList();

        return Optional.of(new Notification(at, Type.feedBlock, level, source, title, messages));
    }

    private static Optional<Notification> createReindexNotification(ConsoleUrls consoleUrls, DeploymentId deployment, String clusterId, Instant at, Cluster cluster) {
        NotificationSource source = NotificationSource.from(deployment, ClusterSpec.Id.from(clusterId));
        String title = "Cluster [%s](%s) in **%s** for **%s.%s** is [reindexing](https://docs.vespa.ai/en/operations/reindexing.html)".formatted(
                clusterId, consoleUrls.clusterReindexing(deployment.applicationId(), deployment.zoneId(), source.clusterId().get()),
                deployment.zoneId().value(), deployment.applicationId().application().value(), deployment.applicationId().instance().value());
        List<String> messages = cluster.ready().entrySet().stream()
                .filter(entry -> entry.getValue().progress().isPresent())
                .map(entry -> Text.format("document type '%s'%s (%.1f%% done)",
                        entry.getKey(), entry.getValue().cause().map(s -> " " + s).orElse(""), 100 * entry.getValue().progress().get()))
                .sorted()
                .toList();
        if (messages.isEmpty()) return Optional.empty();
        return Optional.of(new Notification(at, Type.reindex, Level.info, source, title, messages));
    }

    /**
     * Returns a feed block summary for the given resource: the notification level and
     * notification message for the given resource utilization wrt. given resource limit.
     * If utilization is well below the limit, Optional.empty() is returned.
     */
    private static Optional<Pair<Level, String>> resourceUtilToFeedBlockStatus(
            String resource, Optional<Double> util, Optional<Double> feedBlockLimit) {
        if (util.isEmpty() || feedBlockLimit.isEmpty()) return Optional.empty();
        double utilRelativeToLimit = util.get() / feedBlockLimit.get();
        if (utilRelativeToLimit < 0.95) return Optional.empty();

        String message = Text.format("%s (usage: %.1f%%, feed block limit: %.1f%%)",
                resource, 100 * util.get(), 100 * feedBlockLimit.get());
        if (utilRelativeToLimit < 1) return Optional.of(new Pair<>(Level.warning, message));
        return Optional.of(new Pair<>(Level.error, message));
    }
}
