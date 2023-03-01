// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.path.Path;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentContext;
import com.yahoo.vespa.hosted.controller.integration.ZoneRegistryMock;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import com.yahoo.vespa.hosted.controller.tenant.ArchiveAccess;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Email;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;
import com.yahoo.vespa.hosted.controller.tenant.TenantInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing.Cluster;
import static com.yahoo.vespa.hosted.controller.notification.Notification.Level;
import static com.yahoo.vespa.hosted.controller.notification.Notification.Type;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author freva
 */
public class NotificationsDbTest {

    private static final ApplicationReindexing emptyReindexing = new ApplicationReindexing(false, Map.of());
    private static final TenantName tenant = TenantName.from("tenant1");
    private static final Email email = new Email("user1@example.com", true);
    private static final CloudTenant cloudTenant = new CloudTenant(tenant,
            Instant.now(),
            LastLoginInfo.EMPTY,
            Optional.empty(),
            ImmutableBiMap.of(),
            TenantInfo.empty()
                    .withContacts(new TenantContacts(
                            List.of(new TenantContacts.EmailContact(
                                List.of(TenantContacts.Audience.NOTIFICATIONS),
                            email)))),
            List.of(),
            new ArchiveAccess(),
            Optional.empty(),
            Instant.EPOCH);
    private static final List<Notification> notifications = List.of(
            notification(1001, Type.deployment, Level.error, NotificationSource.from(tenant), "tenant msg"),
            notification(1101, Type.applicationPackage, Level.warning, NotificationSource.from(TenantAndApplicationId.from(tenant.value(), "app1")), "app msg"),
            notification(1201, Type.deployment, Level.error, NotificationSource.from(ApplicationId.from(tenant.value(), "app2", "instance2")), "instance msg"),
            notification(1301, Type.deployment, Level.warning, NotificationSource.from(new DeploymentId(ApplicationId.from(tenant.value(), "app2", "instance2"), ZoneId.from("prod", "us-north-2"))), "deployment msg"),
            notification(1401, Type.feedBlock, Level.error, NotificationSource.from(new DeploymentId(ApplicationId.from(tenant.value(), "app1", "instance1"), ZoneId.from("dev", "us-south-1")), ClusterSpec.Id.from("cluster1")), "cluster msg"),
            notification(1501, Type.deployment, Level.warning, NotificationSource.from(new RunId(ApplicationId.from(tenant.value(), "app1", "instance1"), DeploymentContext.devUsEast1, 4)), "run id msg"));

    private final ManualClock clock = new ManualClock(Instant.ofEpochSecond(12345));
    private final MockCuratorDb curatorDb = new MockCuratorDb(SystemName.Public);
    private final MockMailer mailer = new MockMailer();
    private final FlagSource flagSource = new InMemoryFlagSource().withBooleanFlag(PermanentFlags.NOTIFICATION_DISPATCH_FLAG.id(), true);
    private final NotificationsDb notificationsDb = new NotificationsDb(clock, curatorDb, new Notifier(curatorDb, new ZoneRegistryMock(SystemName.cd), mailer, flagSource));

    @Test
    void list_test() {
        assertEquals(notifications, notificationsDb.listNotifications(NotificationSource.from(tenant), false));
        assertEquals(notificationIndices(0, 1, 2, 3), notificationsDb.listNotifications(NotificationSource.from(tenant), true));
        assertEquals(notificationIndices(2, 3), notificationsDb.listNotifications(NotificationSource.from(TenantAndApplicationId.from(tenant.value(), "app2")), false));
        assertEquals(notificationIndices(4, 5), notificationsDb.listNotifications(NotificationSource.from(ApplicationId.from(tenant.value(), "app1", "instance1")), false));
        assertEquals(notificationIndices(5), notificationsDb.listNotifications(NotificationSource.from(new RunId(ApplicationId.from(tenant.value(), "app1", "instance1"), DeploymentContext.devUsEast1, 5)), false));
        assertEquals(List.of(), notificationsDb.listNotifications(NotificationSource.from(new RunId(ApplicationId.from(tenant.value(), "app1", "instance1"), DeploymentContext.productionUsEast3, 4)), false));
    }

    @Test
    void add_test() {
        Notification notification1 = notification(12345, Type.deployment, Level.warning, NotificationSource.from(ApplicationId.from(tenant.value(), "app2", "instance2")), "instance msg #2");
        Notification notification2 = notification(12345, Type.deployment, Level.error,   NotificationSource.from(ApplicationId.from(tenant.value(), "app3", "instance2")), "instance msg #3");

        // Replace the 3rd notification
        notificationsDb.setNotification(notification1.source(), notification1.type(), notification1.level(), notification1.messages());

        // Notification for a new app, add without replacement
        notificationsDb.setNotification(notification2.source(), notification2.type(), notification2.level(), notification2.messages());

        List<Notification> expected = notificationIndices(0, 1, 3, 4, 5);
        expected.addAll(List.of(notification1, notification2));
        assertEquals(expected, curatorDb.readNotifications(tenant));
    }

    @Test
    void notifier_test() {
        Notification notification1 = notification(12345, Type.deployment, Level.warning, NotificationSource.from(ApplicationId.from(tenant.value(), "app2", "instance2")), "instance msg #2");
        Notification notification2 = notification(12345, Type.applicationPackage, Level.error,   NotificationSource.from(ApplicationId.from(tenant.value(), "app3", "instance2")), "instance msg #3");
        Notification notification3 = notification(12345, Type.reindex, Level.warning, NotificationSource.from(new DeploymentId(ApplicationId.from(tenant.value(), "app2", "instance2"), ZoneId.defaultId()), new ClusterSpec.Id("content")), "instance msg #2");
        ;
        var a = notifications.get(0);
        notificationsDb.setNotification(a.source(), a.type(), a.level(), a.messages());
        assertEquals(0, mailer.inbox(email.getEmailAddress()).size());

        // Replace the 3rd notification. but don't change source or type
        notificationsDb.setNotification(notification1.source(), notification1.type(), notification1.level(), notification1.messages());
        assertEquals(0, mailer.inbox(email.getEmailAddress()).size());

        // Notification for a new app, add without replacement
        notificationsDb.setNotification(notification2.source(), notification2.type(), notification2.level(), notification2.messages());
        assertEquals(1, mailer.inbox(email.getEmailAddress()).size());

        // Notification for new type on existing app
        notificationsDb.setNotification(notification3.source(), notification3.type(), notification3.level(), notification3.messages());
        assertEquals(2, mailer.inbox(email.getEmailAddress()).size());
    }

    @Test
    void remove_single_test() {
        // Remove the 3rd notification
        notificationsDb.removeNotification(NotificationSource.from(ApplicationId.from(tenant.value(), "app2", "instance2")), Type.deployment);

        // Removing something that doesn't exist is OK
        notificationsDb.removeNotification(NotificationSource.from(ApplicationId.from(tenant.value(), "app3", "instance2")), Type.deployment);

        assertEquals(notificationIndices(0, 1, 3, 4, 5), curatorDb.readNotifications(tenant));
    }

    @Test
    void remove_multiple_test() {
        // Remove the 3rd notification
        notificationsDb.removeNotifications(NotificationSource.from(ApplicationId.from(tenant.value(), "app1", "instance1")));
        assertEquals(notificationIndices(0, 1, 2, 3), curatorDb.readNotifications(tenant));
        assertTrue(curatorDb.curator().exists(Path.fromString("/controller/v1/notifications/" + tenant.value())));

        notificationsDb.removeNotifications(NotificationSource.from(tenant));
        assertEquals(List.of(), curatorDb.readNotifications(tenant));
        assertFalse(curatorDb.curator().exists(Path.fromString("/controller/v1/notifications/" + tenant.value())));
    }

    @Test
    void deployment_metrics_notify_test() {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenant.value(), "app1", "instance1"), ZoneId.from("prod", "us-south-3"));

        // No metrics, no new notification
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(), emptyReindexing);
        assertEquals(0, mailer.inbox(email.getEmailAddress()).size());

        // Metrics that contain none of the feed block metrics does not create new notification
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(clusterMetrics("cluster1", null, null, null, null)), emptyReindexing);
        assertEquals(0, mailer.inbox(email.getEmailAddress()).size());

        // One resource is at warning
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(clusterMetrics("cluster1", 0.88, 0.9, 0.3, 0.5)), emptyReindexing);
        assertEquals(1, mailer.inbox(email.getEmailAddress()).size());

        // One resource over the limit
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(clusterMetrics("cluster1", 0.95, 0.9, 0.3, 0.5)), emptyReindexing);
        assertEquals(2, mailer.inbox(email.getEmailAddress()).size());
    }

    @Test
    void feed_blocked_single_cluster_test() {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenant.value(), "app1", "instance1"), ZoneId.from("prod", "us-south-3"));
        NotificationSource sourceCluster1 = NotificationSource.from(deploymentId, ClusterSpec.Id.from("cluster1"));
        List<Notification> expected = new ArrayList<>(notifications);

        // No metrics, no new notification
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(), emptyReindexing);
        assertEquals(expected, curatorDb.readNotifications(tenant));

        // Metrics that contain none of the feed block metrics does not create new notification
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(clusterMetrics("cluster1", null, null, null, null)), emptyReindexing);
        assertEquals(expected, curatorDb.readNotifications(tenant));

        // Metrics that only contain util or limit (should not be possible) should not cause any issues
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(clusterMetrics("cluster1", 0.95, null, null, 0.5)), emptyReindexing);
        assertEquals(expected, curatorDb.readNotifications(tenant));

        // One resource is at warning
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(clusterMetrics("cluster1", 0.88, 0.9, 0.3, 0.5)), emptyReindexing);
        expected.add(notification(12345, Type.feedBlock, Level.warning, sourceCluster1, "disk (usage: 88.0%, feed block limit: 90.0%)"));
        assertEquals(expected, curatorDb.readNotifications(tenant));

        // Both resources over the limit
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(clusterMetrics("cluster1", 0.95, 0.9, 0.3, 0.5)), emptyReindexing);
        expected.set(6, notification(12345, Type.feedBlock, Level.error, sourceCluster1, "disk (usage: 95.0%, feed block limit: 90.0%)"));
        assertEquals(expected, curatorDb.readNotifications(tenant));

        // One resource at warning, one at error: Only show error message
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(clusterMetrics("cluster1", 0.95, 0.9, 0.7, 0.5)), emptyReindexing);
        expected.set(6, notification(12345, Type.feedBlock, Level.error, sourceCluster1,
                "memory (usage: 70.0%, feed block limit: 50.0%)", "disk (usage: 95.0%, feed block limit: 90.0%)"));
        assertEquals(expected, curatorDb.readNotifications(tenant));
    }

    @Test
    void deployment_metrics_multiple_cluster_test() {
        DeploymentId deploymentId = new DeploymentId(ApplicationId.from(tenant.value(), "app1", "instance1"), ZoneId.from("prod", "us-south-3"));
        NotificationSource sourceCluster1 = NotificationSource.from(deploymentId, ClusterSpec.Id.from("cluster1"));
        NotificationSource sourceCluster2 = NotificationSource.from(deploymentId, ClusterSpec.Id.from("cluster2"));
        NotificationSource sourceCluster3 = NotificationSource.from(deploymentId, ClusterSpec.Id.from("cluster3"));
        List<Notification> expected = new ArrayList<>(notifications);

        // Cluster1 and cluster2 are having feed block issues, cluster 3 is reindexing
        ApplicationReindexing applicationReindexing1 = new ApplicationReindexing(true, Map.of(
                "cluster3", new Cluster(Map.of(), Map.of(
                        "announcements", reindexingStatus("reindexing due to a schema change", 0.75),
                        "build", reindexingStatus(null, 0.50)))));
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(
                clusterMetrics("cluster1", 0.88, 0.9, 0.3, 0.5), clusterMetrics("cluster2", 0.6, 0.8, 0.9, 0.75), clusterMetrics("cluster3", 0.1, 0.8, 0.2, 0.9)), applicationReindexing1);
        expected.add(notification(12345, Type.feedBlock, Level.warning, sourceCluster1, "disk (usage: 88.0%, feed block limit: 90.0%)"));
        expected.add(notification(12345, Type.feedBlock, Level.error, sourceCluster2, "memory (usage: 90.0%, feed block limit: 75.0%)"));
        expected.add(notification(12345, Type.reindex, Level.info, sourceCluster3, "document type 'announcements' reindexing due to a schema change (75.0% done)", "document type 'build' (50.0% done)"));
        assertEquals(expected, curatorDb.readNotifications(tenant));

        // Cluster1 improves, while cluster3 starts having feed block issues and finishes reindexing 'build' documents
        ApplicationReindexing applicationReindexing2 = new ApplicationReindexing(true, Map.of(
                "cluster3", new Cluster(Map.of(), Map.of(
                        "announcements", reindexingStatus("reindexing due to a schema change", 0.90),
                        "build", reindexingStatus(null, null)))));
        notificationsDb.setDeploymentMetricsNotifications(deploymentId, List.of(
                clusterMetrics("cluster1", 0.15, 0.9, 0.3, 0.5), clusterMetrics("cluster2", 0.6, 0.8, 0.9, 0.75), clusterMetrics("cluster3", 0.78, 0.8, 0.2, 0.9)), applicationReindexing2);
        expected.set(6, notification(12345, Type.feedBlock, Level.error, sourceCluster2, "memory (usage: 90.0%, feed block limit: 75.0%)"));
        expected.set(7, notification(12345, Type.feedBlock, Level.warning, sourceCluster3, "disk (usage: 78.0%, feed block limit: 80.0%)"));
        expected.set(8, notification(12345, Type.reindex, Level.info, sourceCluster3, "document type 'announcements' reindexing due to a schema change (90.0% done)"));
        assertEquals(expected, curatorDb.readNotifications(tenant));
    }

    @BeforeEach
    public void init() {
        curatorDb.writeNotifications(tenant, notifications);
        curatorDb.writeTenant(cloudTenant);
        mailer.reset();
    }

    private static List<Notification> notificationIndices(int... indices) {
        return Arrays.stream(indices).mapToObj(notifications::get).collect(Collectors.toCollection(ArrayList::new));
    }

    private static Notification notification(long secondsSinceEpoch, Type type, Level level, NotificationSource source, String... messages) {
        return new Notification(Instant.ofEpochSecond(secondsSinceEpoch), type, level, source, List.of(messages));
    }

    private static ClusterMetrics clusterMetrics(String clusterId,
                                                 Double diskUtil, Double diskLimit, Double memoryUtil, Double memoryLimit) {
        Map<String, Double> metrics = new HashMap<>();
        if (diskUtil != null) metrics.put(ClusterMetrics.DISK_UTIL, diskUtil);
        if (diskLimit != null) metrics.put(ClusterMetrics.DISK_FEED_BLOCK_LIMIT, diskLimit);
        if (memoryUtil != null) metrics.put(ClusterMetrics.MEMORY_UTIL, memoryUtil);
        if (memoryLimit != null) metrics.put(ClusterMetrics.MEMORY_FEED_BLOCK_LIMIT, memoryLimit);
        return new ClusterMetrics(clusterId, "content", metrics);
    }

    private static ApplicationReindexing.Status reindexingStatus(String causeOrNull, Double progressOrNull) {
        return new ApplicationReindexing.Status(null, null, null, null, null, progressOrNull, null, causeOrNull);
    }
}
