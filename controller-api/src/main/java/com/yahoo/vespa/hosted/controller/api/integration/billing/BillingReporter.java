package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.vespa.hosted.controller.tenant.BillingReference;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;

public interface BillingReporter {
    BillingReference maintainTenant(CloudTenant tenant);
}
