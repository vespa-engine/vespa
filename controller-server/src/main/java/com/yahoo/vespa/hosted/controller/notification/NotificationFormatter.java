// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.integration.ConsoleUrls;

import java.util.Objects;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.notification.Notifier.notificationLink;

/**
 * Created a NotificationContent for a given Notification.
 *
 * The formatter will create specific summary, message start and URI for a given Notification.
 *
 * @author enygaard
 */
public class NotificationFormatter {
    private final ConsoleUrls consoleUrls;

    public NotificationFormatter(ConsoleUrls consoleUrls) {
        this.consoleUrls = Objects.requireNonNull(consoleUrls);
    }

    public FormattedNotification format(Notification n) {
        return switch (n.type()) {
            case applicationPackage, submission -> applicationPackage(n);
            case deployment -> deployment(n);
            case testPackage -> testPackage(n);
            case reindex -> reindex(n);
            case feedBlock -> feedBlock(n);
            default -> new FormattedNotification(n, n.type().name(), "", consoleUrls.tenantOverview(n.source().tenant()));
        };
    }

    private FormattedNotification applicationPackage(Notification n) {
        var source = n.source();
        var application = requirePresent(source.application(), "application");
        var message = Text.format("Application package for %s%s has %s",
                application,
                source.instance().map(instance -> "." + instance.value()).orElse(""),
                levelText(n.level(), n.messages().size()));
        return new FormattedNotification(n, "Application package", message, notificationLink(consoleUrls, n.source()));
    }

    private FormattedNotification deployment(Notification n) {
        var source = n.source();
        var message = Text.format("%s for %s.%s has %s",
                jobText(source),
                requirePresent(source.application(), "application"),
                requirePresent(source.instance(), "instance"),
                levelText(n.level(), n.messages().size()));
        return new FormattedNotification(n,"Deployment", message, notificationLink(consoleUrls, n.source()));
    }

    private FormattedNotification testPackage(Notification n) {
        var source = n.source();
        var application = requirePresent(source.application(), "application");
        var message = Text.format("There %s with tests for %s%s",
                n.messages().size() > 1 ? "are problems" : "is a problem",
                application,
                source.instance().map(i -> "."+i).orElse(""));
        return new FormattedNotification(n, "Test package", message, notificationLink(consoleUrls, n.source()));
    }

    private FormattedNotification reindex(Notification n) {
        var message = Text.format("%s is reindexing", clusterInfo(n.source()));
        var application = requirePresent(n.source().application(), "application");
        var instance = requirePresent(n.source().instance(), "instance");
        var clusterId = requirePresent(n.source().clusterId(), "clusterId");
        var zone = requirePresent(n.source().zoneId(), "zoneId");
        return new FormattedNotification(n, "Reindex", message,
                consoleUrls.clusterReindexing(ApplicationId.from(n.source().tenant(), application, instance), zone, clusterId));
    }

    private FormattedNotification feedBlock(Notification n) {
        String type = n.level() == Notification.Level.warning ? "Nearly feed blocked" : "Feed blocked";
        var message = Text.format("%s is %s", clusterInfo(n.source()), type.toLowerCase());
        return new FormattedNotification(n, type, message, notificationLink(consoleUrls, n.source()));
    }

    private String jobText(NotificationSource source) {
        var jobType = requirePresent(source.jobType(), "jobType");
        var zone = jobType.zone();
        var runNumber = source.runNumber().orElseThrow(() -> new MissingOptionalException("runNumber"));
        switch (zone.environment().value()) {
            case "production":
                return Text.format("Deployment job #%d to %s", runNumber, zone.region());
            case "test":
                return Text.format("Test job #%d to %s", runNumber, zone.region());
            case "dev":
            case "perf":
                return Text.format("Deployment job #%d to %s.%s", runNumber, zone.environment().value(), zone.region().value());
        }
        switch (jobType.jobName()) {
            case "system-test":
            case "staging-test":
        }
        return Text.format("%s #%d", jobType.jobName(), runNumber);
    }

    private String levelText(Notification.Level level, int count) {
        return switch (level) {
            case error -> "failed";
            case warning -> count > 1 ? Text.format("%d warnings", count) : "a warning";
            default -> count > 1 ? Text.format("%d messages", count) : "a message";
        };
    }

    private String clusterInfo(NotificationSource source) {
        var application = requirePresent(source.application(), "application");
        var instance = requirePresent(source.instance(), "instance");
        var zone = requirePresent(source.zoneId(), "zoneId");
        var clusterId = requirePresent(source.clusterId(), "clusterId");
        return Text.format("Cluster %s in %s.%s for %s.%s",
                clusterId.value(),
                zone.environment(), zone.region(),
                application, instance);
    }


    private static <T> T requirePresent(Optional<T> optional, String field) {
        return optional.orElseThrow(() -> new MissingOptionalException(field));
    }
}
