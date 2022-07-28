package com.yahoo.vespa.hosted.controller.notification;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author enygaard
 */
public class NotificationFormatterTest {
    private final TenantName tenant = TenantName.from("scoober");
    private final ApplicationName application = ApplicationName.from("myapp");
    private final InstanceName instance = InstanceName.from("beta");
    private final ApplicationId applicationId = ApplicationId.from(tenant, application, instance);
    private final DeploymentId deploymentId = new DeploymentId(applicationId, ZoneId.defaultId());
    private final ClusterSpec.Id cluster = new ClusterSpec.Id("content");
    private final ZoneRegistryMock zoneRegistry = new ZoneRegistryMock(SystemName.Public);

    private final NotificationFormatter formatter = new NotificationFormatter(zoneRegistry);

    @Test
    void applicationPackage() {
        var notification = new Notification(Instant.now(), Notification.Type.applicationPackage, Notification.Level.warning, NotificationSource.from(applicationId), List.of("1", "2"));
        var content = formatter.format(notification);
        assertEquals("Application package", content.prettyType());
        assertEquals("Application package for myapp.beta has 2 warnings", content.messagePrefix());
        assertEquals("https://dashboard.tld/scoober.myapp.beta", content.uri().toString());
    }

    @Test
    void deployment() {
        var runId = new RunId(applicationId, JobType.prod(RegionName.defaultName()), 1001);
        var notification = new Notification(Instant.now(), Notification.Type.deployment, Notification.Level.warning, NotificationSource.from(runId), List.of("1"));
        var content = formatter.format(notification);
        assertEquals("Deployment", content.prettyType());
        assertEquals("production-default #1001 for myapp.beta has a warning", content.messagePrefix());
        assertEquals("https://dashboard.tld/scoober.myapp.beta/production-default/1001", content.uri().toString());
    }

    @Test
    void deploymentError() {
        var runId = new RunId(applicationId, JobType.prod(RegionName.defaultName()), 1001);
        var notification = new Notification(Instant.now(), Notification.Type.deployment, Notification.Level.error, NotificationSource.from(runId), List.of("1"));
        var content = formatter.format(notification);
        assertEquals("Deployment", content.prettyType());
        assertEquals("production-default #1001 for myapp.beta has failed", content.messagePrefix());
        assertEquals("https://dashboard.tld/scoober.myapp.beta/production-default/1001", content.uri().toString());
    }

    @Test
    void testPackage() {
        var notification = new Notification(Instant.now(), Notification.Type.testPackage, Notification.Level.warning, NotificationSource.from(TenantAndApplicationId.from(applicationId)), List.of("1"));
        var content = formatter.format(notification);
        assertEquals("Test package", content.prettyType());
        assertEquals("There is a problem with tests for myapp", content.messagePrefix());
        assertEquals("https://dashboard.tld/scoober/myapp", content.uri().toString());
    }

    @Test
    void reindex() {
        var notification = new Notification(Instant.now(), Notification.Type.reindex, Notification.Level.info, NotificationSource.from(deploymentId, cluster), List.of("1"));
        var content = formatter.format(notification);
        assertEquals("Reindex", content.prettyType());
        assertEquals("Cluster content in prod.default for myapp.beta is reindexing", content.messagePrefix());
        assertEquals("https://dashboard.tld/scoober.myapp.beta?beta.prod.default=clusters%2Ccontent%3Dstatus", content.uri().toString());
    }

    @Test
    void feedBlock() {
        var notification = new Notification(Instant.now(), Notification.Type.feedBlock, Notification.Level.warning, NotificationSource.from(deploymentId, cluster), List.of("1"));
        var content = formatter.format(notification);
        assertEquals("Nearly feed blocked", content.prettyType());
        assertEquals("Cluster content in prod.default for myapp.beta is nearly feed blocked", content.messagePrefix());
        assertEquals("https://dashboard.tld/scoober.myapp.beta?beta.prod.default=clusters%2Ccontent", content.uri().toString());
    }
}