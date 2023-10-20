// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
import com.yahoo.vespa.hosted.controller.deployment.ApplicationPackageBuilder;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentTester;
import com.yahoo.vespa.hosted.controller.notification.Notification;
import com.yahoo.vespa.hosted.controller.notification.NotificationSource;
import com.yahoo.vespa.hosted.controller.tenant.LastLoginInfo;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author ogronnesby
 */
public class CloudTrialExpirerTest {

    private static final boolean OVERWRITE_TEST_FILES = false;

    private final ControllerTester tester = new ControllerTester(SystemName.PublicCd);
    private final DeploymentTester deploymentTester = new DeploymentTester(tester);
    private final CloudTrialExpirer expirer = new CloudTrialExpirer(tester.controller(), Duration.ofMinutes(5));

    @Test
    void expire_inactive_tenant() {
        registerTenant("trial-tenant", "trial", Duration.ofDays(14).plusMillis(1));
        assertEquals(0.0, expirer.maintain());
        assertPlan("trial-tenant", "none");
    }

    @Test
    void tombstone_inactive_none() {
        registerTenant("none-tenant", "none", Duration.ofDays(91).plusMillis(1));
        assertEquals(0.0, expirer.maintain());
        assertEquals(Tenant.Type.deleted, tester.controller().tenants().get(TenantName.from("none-tenant"), true).get().type());
    }

    @Test
    void keep_inactive_nontrial_tenants() {
        registerTenant("not-a-trial-tenant", "pay-as-you-go", Duration.ofDays(30));
        assertEquals(0.0, expirer.maintain());
        assertPlan("not-a-trial-tenant", "pay-as-you-go");
    }

    @Test
    void keep_active_trial_tenants() {
        registerTenant("active-trial-tenant", "trial", Duration.ofHours(14).minusMillis(1));
        assertEquals(0.0, expirer.maintain());
        assertPlan("active-trial-tenant", "trial");
    }

    @Test
    void keep_inactive_exempt_tenants() {
        registerTenant("exempt-trial-tenant", "trial", Duration.ofDays(40));
        ((InMemoryFlagSource) tester.controller().flagSource()).withListFlag(PermanentFlags.EXTENDED_TRIAL_TENANTS.id(), List.of("exempt-trial-tenant"), String.class);
        assertEquals(0.0, expirer.maintain());
        assertPlan("exempt-trial-tenant", "trial");
    }

    @Test
    void keep_inactive_trial_tenants_with_deployments() {
        registerTenant("with-deployments", "trial", Duration.ofDays(30));
        registerDeployment("with-deployments", "my-app", "default");
        assertEquals(0.0, expirer.maintain());
        assertPlan("with-deployments", "trial");
    }

    @Test
    void delete_tenants_with_applications_with_no_deployments() {
        registerTenant("with-apps", "trial", Duration.ofDays(184));
        tester.createApplication("with-apps", "app1", "instance1");
        assertEquals(0.0, expirer.maintain());
        assertPlan("with-apps", "none");
        assertEquals(0.0, expirer.maintain());
        assertTrue(tester.controller().tenants().get("with-apps").isEmpty());
    }

    @Test
    void keep_tenants_without_applications_that_are_idle() {
        registerTenant("active", "none", Duration.ofDays(182));
        assertEquals(0.0, expirer.maintain());
        assertPlan("active", "none");
    }

    @Test
    void queues_trial_notification_based_on_account_age() throws IOException {
        var clock = (ManualClock)tester.controller().clock();
        var mailer = (MockMailer) tester.serviceRegistry().mailer();
        var tenant = TenantName.from("trial-tenant");
        ((InMemoryFlagSource) tester.controller().flagSource())
                .withBooleanFlag(Flags.CLOUD_TRIAL_NOTIFICATIONS.id(), true);
        registerTenant(tenant.value(), "trial", Duration.ZERO);
        assertEquals(0.0, expirer.maintain());
        assertEquals("Welcome to Vespa Cloud", lastAccountLevelNotificationTitle(tenant));
        assertLastEmailEquals(mailer, "welcome.html");

        clock.advance(Duration.ofDays(7));
        assertEquals(0.0, expirer.maintain());
        assertEquals("How is your Vespa Cloud trial going?", lastAccountLevelNotificationTitle(tenant));
        assertLastEmailEquals(mailer, "trial-reminder.html");

        clock.advance(Duration.ofDays(5));
        assertEquals(0.0, expirer.maintain());
        assertEquals("Your Vespa Cloud trial expires in 2 days", lastAccountLevelNotificationTitle(tenant));
        assertLastEmailEquals(mailer, "trial-expiring-soon.html");

        clock.advance(Duration.ofDays(1));
        assertEquals(0.0, expirer.maintain());
        assertEquals("Your Vespa Cloud trial expires tomorrow", lastAccountLevelNotificationTitle(tenant));
        assertLastEmailEquals(mailer, "trial-expiring-immediately.html");

        clock.advance(Duration.ofDays(2));
        assertEquals(0.0, expirer.maintain());
        assertEquals("Your Vespa Cloud trial has expired", lastAccountLevelNotificationTitle(tenant));
        assertLastEmailEquals(mailer, "trial-expired.html");
    }

    private void assertLastEmailEquals(MockMailer mailer, String expectedContentFile) throws IOException {
        var mails = mailer.inbox("dev-trial-tenant");
        assertFalse(mails.isEmpty());
        var content = mails.get(mails.size() - 1).htmlMessage().orElseThrow();
        var path = Paths.get("src/test/resources/mail/" + expectedContentFile);
        if (OVERWRITE_TEST_FILES) {
            Files.write(path, content.getBytes(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } else {
            var expectedContent = Files.readString(path);
            assertEquals(expectedContent, content);
        }
    }

    private void registerTenant(String tenantName, String plan, Duration timeSinceLastLogin) {
        var name = TenantName.from(tenantName);
        tester.createTenant(tenantName, Tenant.Type.cloud);
        tester.serviceRegistry().billingController().setPlan(name, PlanId.from(plan), false, false);
        tester.controller().tenants().updateLastLogin(name, List.of(LastLoginInfo.UserLevel.user), tester.controller().clock().instant().minus(timeSinceLastLogin));
    }

    private void registerDeployment(String tenantName, String appName, String instanceName) {
        var app = tester.createApplication(tenantName, appName, instanceName);
        var ctx = deploymentTester.newDeploymentContext(tenantName, appName, instanceName);
        var pkg = new ApplicationPackageBuilder()
                .instances("default")
                .region("aws-us-east-1c")
                .trustDefaultCertificate()
                .build();
        ctx.submit(pkg).deploy();
    }

    private void assertPlan(String tenant, String planId) {
        assertEquals(planId, tester.serviceRegistry().billingController().getPlan(TenantName.from(tenant)).value());
    }

    private String lastAccountLevelNotificationTitle(TenantName tenant) {
        return tester.controller().notificationsDb()
                .listNotifications(NotificationSource.from(tenant), false).stream()
                .filter(n -> n.type() == Notification.Type.account).map(Notification::title)
                .findFirst().orElseThrow();
    }

}
