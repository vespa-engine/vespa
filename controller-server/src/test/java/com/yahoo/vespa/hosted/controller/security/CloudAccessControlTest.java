// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.security;

import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.hosted.controller.api.integration.ServiceRegistry;
import com.yahoo.vespa.hosted.controller.api.integration.billing.Invoice;
import com.yahoo.vespa.hosted.controller.api.integration.billing.MockBillingController;
import com.yahoo.vespa.hosted.controller.api.integration.billing.PlanId;
import com.yahoo.vespa.hosted.controller.api.integration.stubs.MockUserManagement;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;
import com.yahoo.vespa.hosted.controller.integration.ServiceRegistryMock;
import org.junit.Test;

import javax.ws.rs.ForbiddenException;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.HashSet;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author olaa
 */
public class CloudAccessControlTest {

    private final UserManagement userManagement = new MockUserManagement();
    private final FlagSource flagSource = new InMemoryFlagSource();
    private final ServiceRegistry serviceRegistry = new ServiceRegistryMock();
    private final MockBillingController billingController = (MockBillingController) serviceRegistry.billingController();
    private final CloudAccessControl cloudAccessControl = new CloudAccessControl(userManagement, flagSource, serviceRegistry);

    @Test
    public void tenant_deletion_fails_when_outstanding_charges() {
        // First verify that it works with no outstanding charges
        var tenant = TenantName.defaultName();
        var principal = mock(Principal.class);
        var credentials = new Auth0Credentials(principal, new HashSet<>());
        cloudAccessControl.deleteTenant(tenant, credentials);

        // Forbidden if plan != trial
        billingController.setPlan(tenant, PlanId.from("subscription"), false);
        try {
            cloudAccessControl.deleteTenant(tenant, credentials);
            fail();
        } catch (ForbiddenException ignored) {}
        billingController.setPlan(tenant, PlanId.from("trial"), false);

        // Forbidden if outstanding lineitems
        billingController.addLineItem(tenant, "Some expense", BigDecimal.TEN, "agent");
        try {
            cloudAccessControl.deleteTenant(tenant, credentials);
            fail();
        } catch (ForbiddenException ignored) {}
        billingController.deleteLineItem("line-item-id");

        // Forbidden if uncommited invoice exists
        var invoice = mock(Invoice.class);
        when(invoice.sum()).thenReturn(BigDecimal.TEN);
        billingController.addInvoice(tenant, invoice, false);
        try {
            cloudAccessControl.deleteTenant(tenant, credentials);
            fail();
        } catch (ForbiddenException ignored) {}

    }
}