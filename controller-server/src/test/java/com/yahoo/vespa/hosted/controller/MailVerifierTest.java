// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockMailer;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import com.yahoo.vespa.hosted.controller.tenant.Email;
import com.yahoo.vespa.hosted.controller.tenant.PendingMailVerification;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;
import com.yahoo.vespa.hosted.controller.tenant.TenantContacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author olaa
 */
class MailVerifierTest {

    private final ControllerTester tester = new ControllerTester(SystemName.Public);
    private final MockMailer mailer = tester.serviceRegistry().mailer();
    private final MailVerifier mailVerifier = new MailVerifier(tester.controller().tenants(), mailer, tester.curator(), tester.clock());

    private static final TenantName tenantName = TenantName.from("scoober");
    private static final String mail = "unverified@bar.com";
    private static final List<TenantContacts.Audience> audiences = List.of(TenantContacts.Audience.NOTIFICATIONS, TenantContacts.Audience.TENANT);

    @BeforeEach
    public void setup() {
        tester.createTenant(tenantName.value(), Tenant.Type.cloud);

        tester.controller().tenants().lockOrThrow(tenantName, LockedTenant.Cloud.class, lockedTenant -> {
            var contacts = List.of(
                    new TenantContacts.EmailContact(audiences, new Email("verified@bar.com", true)),
                    new TenantContacts.EmailContact(audiences, new Email(mail, false)),
                    new TenantContacts.EmailContact(audiences, new Email("another-unverified@bar.com", false))
            );
            lockedTenant = lockedTenant.withInfo(lockedTenant.get().info().withContacts(new TenantContacts(contacts)));
            tester.controller().tenants().store(lockedTenant);
        });
    }

    @Test
    public void test_new_mail_verification() {
        mailVerifier.sendMailVerification(tenantName, mail, PendingMailVerification.MailType.NOTIFICATIONS);

        // Verify mail is sent
        var expectedMail = "message";
        assertEquals(1, mailer.inbox(mail).size());
        assertEquals(expectedMail, mailer.inbox(mail).get(0).message());

        // Verify ZK data is updated
        var writtenMailVerification = tester.curator().listPendingMailVerifications().get(0);
        assertEquals(PendingMailVerification.MailType.NOTIFICATIONS, writtenMailVerification.getMailType());
        assertEquals(tenantName, writtenMailVerification.getTenantName());
        assertEquals(tester.clock().instant().plus(Duration.ofDays(7)), writtenMailVerification.getVerificationDeadline());
        assertEquals(mail, writtenMailVerification.getMailAddress());

        // Mail verification is no-op if deadline has passed
        tester.clock().advance(Duration.ofDays(14));
        assertFalse(mailVerifier.verifyMail(writtenMailVerification.getVerificationCode()));
        assertFalse(tester.curator().listPendingMailVerifications().isEmpty());

        // Mail is verified
        tester.clock().retreat(Duration.ofDays(14));
        mailVerifier.verifyMail(writtenMailVerification.getVerificationCode());
        assertTrue(tester.curator().listPendingMailVerifications().isEmpty());
        var tenant = tester.controller().tenants().require(tenantName, CloudTenant.class);
        var expectedContacts = List.of(
                new TenantContacts.EmailContact(audiences, new Email("verified@bar.com", true)),
                new TenantContacts.EmailContact(audiences, new Email(mail, true)),
                new TenantContacts.EmailContact(audiences, new Email("another-unverified@bar.com", false))
        );
        assertEquals(expectedContacts, tenant.info().contacts().all());
    }

    @Test
    public void resending_verification_deletes_old_one() {
        var pendingMailVerification = mailVerifier.sendMailVerification(tenantName, mail, PendingMailVerification.MailType.NOTIFICATIONS);
        var tenant = tester.controller().tenants().require(tenantName, CloudTenant.class);

        // Unknown mail is no-op
        var resentVerification = mailVerifier.resendMailVerification(tenantName, "unknown-mail", PendingMailVerification.MailType.NOTIFICATIONS);
        assertTrue(resentVerification.isEmpty());
        assertTrue(tester.curator().getPendingMailVerification(pendingMailVerification.getVerificationCode()).isPresent());

        // Verification mail is re-sent, old data is replaced
        resentVerification = mailVerifier.resendMailVerification(tenantName, mail, PendingMailVerification.MailType.NOTIFICATIONS);
        assertTrue(resentVerification.isPresent());
        assertTrue(tester.curator().getPendingMailVerification(pendingMailVerification.getVerificationCode()).isEmpty());
        assertTrue(tester.curator().getPendingMailVerification(resentVerification.get().getVerificationCode()).isPresent());
    }

}