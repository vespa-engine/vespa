// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.notification;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.path.Path;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;
import com.yahoo.vespa.hosted.controller.application.TenantAndApplicationId;
import com.yahoo.vespa.hosted.controller.persistence.MockCuratorDb;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author freva
 */
public class NotificationsDbTest {

    private static final TenantName tenant = TenantName.from("tenant1");
    private static final List<Notification> notifications = List.of(
            notification(1001, Notification.Type.DEPLOYMENT_FAILURE, NotificationSource.from(tenant), "tenant msg"),
            notification(1101, Notification.Type.DEPLOYMENT_FAILURE, NotificationSource.from(TenantAndApplicationId.from(tenant.value(), "app1")), "app msg"),
            notification(1201, Notification.Type.DEPLOYMENT_FAILURE, NotificationSource.from(ApplicationId.from(tenant.value(), "app2", "instance2")), "instance msg"),
            notification(1301, Notification.Type.DEPLOYMENT_FAILURE, NotificationSource.from(new DeploymentId(ApplicationId.from(tenant.value(), "app2", "instance2"), ZoneId.from("prod", "us-north-2"))), "deployment msg"),
            notification(1401, Notification.Type.DEPLOYMENT_FAILURE, NotificationSource.from(new DeploymentId(ApplicationId.from(tenant.value(), "app1", "instance1"), ZoneId.from("dev", "us-south-1")), ClusterSpec.Id.from("cluster1")), "cluster msg"),
            notification(1501, Notification.Type.DEPLOYMENT_FAILURE, NotificationSource.from(new RunId(ApplicationId.from(tenant.value(), "app1", "instance1"), JobType.devUsEast1, 4)), "run id msg"));

    private final ManualClock clock = new ManualClock(Instant.ofEpochSecond(12345));
    private final MockCuratorDb curatorDb = new MockCuratorDb();
    private final NotificationsDb notificationsDb = new NotificationsDb(clock, curatorDb);

    @Test
    public void list_test() {
        assertEquals(notifications, notificationsDb.listNotifications(NotificationSource.from(tenant), false));
        assertEquals(notificationIndices(0, 1, 3), notificationsDb.listNotifications(NotificationSource.from(tenant), true));
        assertEquals(notificationIndices(2, 3), notificationsDb.listNotifications(NotificationSource.from(TenantAndApplicationId.from(tenant.value(), "app2")), false));
        assertEquals(notificationIndices(4, 5), notificationsDb.listNotifications(NotificationSource.from(ApplicationId.from(tenant.value(), "app1", "instance1")), false));
        assertEquals(notificationIndices(5), notificationsDb.listNotifications(NotificationSource.from(new RunId(ApplicationId.from(tenant.value(), "app1", "instance1"), JobType.devUsEast1, 5)), false));
        assertEquals(List.of(), notificationsDb.listNotifications(NotificationSource.from(new RunId(ApplicationId.from(tenant.value(), "app1", "instance1"), JobType.productionUsEast3, 4)), false));
    }

    @Test
    public void add_test() {
        Notification notification1 = notification(12345, Notification.Type.DEPLOYMENT_FAILURE, NotificationSource.from(ApplicationId.from(tenant.value(), "app2", "instance2")), "instance msg #2");
        Notification notification2 = notification(12345, Notification.Type.DEPLOYMENT_FAILURE, NotificationSource.from(ApplicationId.from(tenant.value(), "app3", "instance2")), "instance msg #3");

        // Replace the 3rd notification
        notificationsDb.setNotification(notification1.source(), notification1.type(), notification1.messages());

        // Notification for a new app, add without replacement
        notificationsDb.setNotification(notification2.source(), notification2.type(), notification2.messages());

        List<Notification> expected = notificationIndices(0, 1, 3, 4, 5);
        expected.addAll(List.of(notification1, notification2));
        assertEquals(expected, curatorDb.readNotifications(tenant));
    }

    @Test
    public void remove_single_test() {
        // Remove the 3rd notification
        notificationsDb.removeNotification(NotificationSource.from(ApplicationId.from(tenant.value(), "app2", "instance2")), Notification.Type.DEPLOYMENT_FAILURE);

        // Removing something that doesn't exist is OK
        notificationsDb.removeNotification(NotificationSource.from(ApplicationId.from(tenant.value(), "app3", "instance2")), Notification.Type.DEPLOYMENT_FAILURE);

        assertEquals(notificationIndices(0, 1, 3, 4, 5), curatorDb.readNotifications(tenant));
    }

    @Test
    public void remove_multiple_test() {
        // Remove the 3rd notification
        notificationsDb.removeNotifications(NotificationSource.from(ApplicationId.from(tenant.value(), "app1", "instance1")));
        assertEquals(notificationIndices(0, 1, 2, 3), curatorDb.readNotifications(tenant));
        assertTrue(curatorDb.curator().exists(Path.fromString("/controller/v1/notifications/" + tenant.value())));

        notificationsDb.removeNotifications(NotificationSource.from(tenant));
        assertEquals(List.of(), curatorDb.readNotifications(tenant));
        assertFalse(curatorDb.curator().exists(Path.fromString("/controller/v1/notifications/" + tenant.value())));
    }

    @Before
    public void init() {
        curatorDb.writeNotifications(tenant, notifications);
    }

    private static List<Notification> notificationIndices(int... indices) {
        return Arrays.stream(indices).mapToObj(notifications::get).collect(Collectors.toCollection(ArrayList::new));
    }

    private static Notification notification(long secondsSinceEpoch, Notification.Type type, NotificationSource source, String... messages) {
        return new Notification(Instant.ofEpochSecond(secondsSinceEpoch), type, source, List.of(messages));
    }
}
