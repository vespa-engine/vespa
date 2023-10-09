// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanRegistryMock;
import com.yahoo.vespa.hosted.controller.tenant.BillingReference;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BillingReportMaintainerTest {
    private final ControllerTester tester = new ControllerTester(SystemName.PublicCd);
    private final BillingReportMaintainer maintainer = new BillingReportMaintainer(tester.controller(), Duration.ofMinutes(10));

    @Test
    void only_billable_tenants_are_maintained() {
        var t1 = tester.createTenant("t1");
        var t2  = tester.createTenant("t2");

        tester.controller().serviceRegistry().billingController().setPlan(t1, PlanRegistryMock.paidPlan.id(), false, true);
        maintainer.maintain();

        var b1 = billingReference(t1);
        var b2 = billingReference(t2);

        assertFalse(b1.isEmpty());
        assertTrue(b2.isEmpty());

        assertEquals(tester.clock().instant(), b1.orElseThrow().updated());
        assertNotNull(b1.orElseThrow().reference());
    }

    private Optional<BillingReference> billingReference(TenantName tenantName) {
        var t = tester.controller().tenants().require(tenantName, CloudTenant.class);
        return t.billingReference();
    }
}
