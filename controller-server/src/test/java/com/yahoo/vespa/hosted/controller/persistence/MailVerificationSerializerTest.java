// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.tenant.PendingMailVerification;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author olaa
 */
public class MailVerificationSerializerTest {

    @Test
    public void test_serialization() {
        var original = new PendingMailVerification(TenantName.from("test-tenant"),
                "email@mycomp.any",
                "xyz-123",
                Instant.now().truncatedTo(ChronoUnit.MILLIS),
                PendingMailVerification.MailType.TENANT_CONTACT
        );

        var serialized = MailVerificationSerializer.toSlime(original);
        var deserialized = MailVerificationSerializer.fromSlime(serialized);
        assertEquals(original, deserialized);
    }
}