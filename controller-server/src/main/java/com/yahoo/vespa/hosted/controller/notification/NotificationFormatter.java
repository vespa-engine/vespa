package com.yahoo.vespa.hosted.controller.notification;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.text.Text;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import org.apache.http.client.utils.URIBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Created a NotificationContent for a given Notification.
 *
 * The formatter will create specific summary, message start and URI for a given Notification.
 *
 * @author enygaard
 */
public class NotificationFormatter {
    private final ZoneRegistry zoneRegistry;

    public NotificationFormatter(ZoneRegistry zoneRegistry) {
        this.zoneRegistry = Objects.requireNonNull(zoneRegistry);
    }

    public FormattedNotification format(Notification n) {
        return switch (n.type()) {
            case applicationPackage, submission -> applicationPackage(n);
            case deployment -> deployment(n);
            case testPackage -> testPackage(n);
            case reindex -> reindex(n);
            case feedBlock -> feedBlock(n);
            default -> new FormattedNotification(n, n.type().name(), "", zoneRegistry.dashboardUrl(n.source().tenant()));
        };
    }

    private FormattedNotification applicationPackage(Notification n) {
        var source = n.source();
        var application = requirePresent(source.application(), "application");
        var instance = requirePresent(source.instance(), "instance");
        var message = Text.format("Application package for %s.%s has %s",
                application,
                instance,
                levelText(n.level(), n.messages().size()));
        var uri = zoneRegistry.dashboardUrl(ApplicationId.from(source.tenant(), application, instance));
        return new FormattedNotification(n, "Application package", message, uri);
    }

    private FormattedNotification deployment(Notification n) {
        var source = n.source();
        var message = Text.format("%s for %s.%s has %s",
                jobText(source),
                requirePresent(source.application(), "application"),
                requirePresent(source.instance(), "instance"),
                levelText(n.level(), n.messages().size()));
        return new FormattedNotification(n,"Deployment", message, jobLink(n.source()));
    }

    private FormattedNotification testPackage(Notification n) {
        var source = n.source();
        var application = requirePresent(source.application(), "application");
        var message = Text.format("There %s with tests for %s%s",
                n.messages().size() > 1 ? "are problems" : "is a problem",
                application,
                source.instance().map(i -> "."+i).orElse(""));
        var uri = zoneRegistry.dashboardUrl(source.tenant(), application);
        return new FormattedNotification(n, "Test package", message, uri);
    }

    private FormattedNotification reindex(Notification n) {
        var message = Text.format("%s is reindexing", clusterInfo(n.source()));
        var source = n.source();
        var application = requirePresent(source.application(), "application");
        var instance = requirePresent(source.instance(), "instance");
        var clusterId = requirePresent(source.clusterId(), "clusterId");
        var zone = requirePresent(source.zoneId(), "zoneId");
        var instanceURI = zoneRegistry.dashboardUrl(ApplicationId.from(source.tenant(), application, instance));
        try {
            var uri = new URIBuilder(instanceURI)
                    .setParameter(
                            String.format("%s.%s.%s", instance, zone.environment(), zone.region()),
                            String.format("clusters,%s=status", clusterId.value()))
                    .build();
            return new FormattedNotification(n, "Reindex", message, uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private FormattedNotification feedBlock(Notification n) {
        String type;
        if (n.level() == Notification.Level.warning) {
            type = "Nearly feed blocked";
        } else {
            type = "Feed blocked";
        }
        var message = Text.format("%s is %s", clusterInfo(n.source()), type.toLowerCase());
        var source = n.source();
        var application = requirePresent(source.application(), "application");
        var instance = requirePresent(source.instance(), "instance");
        var clusterId = requirePresent(source.clusterId(), "clusterId");
        var zone = requirePresent(source.zoneId(), "zoneId");
        var instanceURI = zoneRegistry.dashboardUrl(ApplicationId.from(source.tenant(), application, instance));
        try {
            var uri = new URIBuilder(instanceURI)
                    .setParameter(
                            String.format("%s.%s.%s", instance, zone.environment(), zone.region()),
                            String.format("clusters,%s", clusterId.value()))
                    .build();
            return new FormattedNotification(n, type, message, uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private URI jobLink(NotificationSource source) {
        var application = requirePresent(source.application(), "application");
        var instance = requirePresent(source.instance(), "instance");
        var jobType = requirePresent(source.jobType(), "jobType");
        var runNumber = source.runNumber().orElseThrow(() -> new MissingOptionalException("runNumber"));
        var applicationId = ApplicationId.from(source.tenant(), application, instance);
        Function<Environment, URI> link = (Environment env) -> zoneRegistry.dashboardUrl(new RunId(applicationId, jobType, runNumber));
        var environment = jobType.zone().environment();
        return switch (environment) {
            case dev, perf -> link.apply(environment);
            default -> link.apply(Environment.prod);
        };
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
