// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.SystemName;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.vespa.hosted.controller.api.integration.billing.BillingDatabaseClientMock;
import com.yahoo.vespa.hosted.controller.api.integration.resource.ResourceDatabaseClientMock;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author olaa
 */
class DatabaseMaintainerTest {

    private ControllerTester tester;
    private DatabaseMaintainer databaseMaintainer;
    private ResourceDatabaseClientMock resourceDatabase;
    private BillingDatabaseClientMock billingDatabase;

    @Test
    public void only_maintain_billing_db_in_public_cd() {
        setSystem(SystemName.Public);
        assertFalse(resourceDatabase.hasRefreshedMaterializedView());
        assertFalse(billingDatabase.isMaintained());
        databaseMaintainer.maintain();
        assertTrue(resourceDatabase.hasRefreshedMaterializedView());
        assertFalse(billingDatabase.isMaintained());

        setSystem(SystemName.PublicCd);
        assertFalse(resourceDatabase.hasRefreshedMaterializedView());
        assertFalse(billingDatabase.isMaintained());
        databaseMaintainer.maintain();
        assertTrue(resourceDatabase.hasRefreshedMaterializedView());
        assertTrue(billingDatabase.isMaintained());
    }

    private void setSystem(SystemName system) {
        tester = new ControllerTester(system);
        databaseMaintainer = new DatabaseMaintainer(tester.controller(), Duration.ofMinutes(30));
        resourceDatabase = tester.serviceRegistry().resourceDatabase();
        billingDatabase = tester.serviceRegistry().billingDatabase();
    }
}
