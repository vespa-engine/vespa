package com.yahoo.vespa.hosted.controller.notification;

import com.google.common.collect.ImmutableBiMap;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
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

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class NotifierTest {
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


    MockCuratorDb curatorDb = new MockCuratorDb(SystemName.Public);

    @BeforeEach
    public void init() {
        curatorDb.writeTenant(cloudTenant);
    }

    @Test
    void dispatch() throws IOException {
        var mailer = new MockMailer();
        var flagSource = new InMemoryFlagSource().withBooleanFlag(PermanentFlags.NOTIFICATION_DISPATCH_FLAG.id(), true);
        var notifier = new Notifier(curatorDb, new ZoneRegistryMock(SystemName.cd), mailer, flagSource);

        var notification = new Notification(Instant.now(), Notification.Type.testPackage, Notification.Level.warning,
                NotificationSource.from(ApplicationId.from(tenant, ApplicationName.defaultName(), InstanceName.defaultName())),
                List.of("test package has production tests, but no production tests are declared in deployment.xml",
                        "see https://docs.vespa.ai/en/testing.html for details on how to write system tests for Vespa"));
        notifier.dispatch(notification);
        assertEquals(1, mailer.inbox(email.getEmailAddress()).size());
        var mail = mailer.inbox(email.getEmailAddress()).get(0);

        assertEquals("[WARNING] Test package Vespa Notification for tenant1.default.default", mail.subject());
        assertEquals(new String(NotifierTest.class.getResourceAsStream("/mail/notification.txt").readAllBytes()), mail.htmlMessage().get());
    }

    @Test
    void linkify() {
        var data = Map.of(
                "Hello. https://example.com/foo/bar.html is a nice place.", "Hello. <a href=\"https://example.com/foo/bar.html\">https://example.com/foo/bar.html</a> is a nice place.",
                "No url.", "No url.");
        data.forEach((input, expected) -> assertEquals(expected, Notifier.linkify(input)));
    }
}
