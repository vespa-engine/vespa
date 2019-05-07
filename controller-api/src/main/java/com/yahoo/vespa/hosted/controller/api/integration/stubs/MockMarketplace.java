package com.yahoo.vespa.hosted.controller.api.integration.stubs;

import com.yahoo.vespa.hosted.controller.api.integration.organization.BillingInfo;
import com.yahoo.vespa.hosted.controller.api.integration.organization.Marketplace;

/**
 * @author jonmv
 */
public class MockMarketplace implements Marketplace {

    @Override
    public BillingInfo resolveCustomer(String registrationToken) {
        return new BillingInfo("customer", "Vespa");
    }

}
