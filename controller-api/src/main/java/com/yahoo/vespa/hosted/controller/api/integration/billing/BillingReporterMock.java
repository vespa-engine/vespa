// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.billing;

import com.yahoo.vespa.hosted.controller.tenant.BillingReference;
import com.yahoo.vespa.hosted.controller.tenant.CloudTenant;

import java.time.Clock;
import java.util.UUID;

public class BillingReporterMock implements BillingReporter {
    private final Clock clock;

    public BillingReporterMock(Clock clock) {
        this.clock = clock;
    }

    @Override
    public BillingReference maintainTenant(CloudTenant tenant) {
        return new BillingReference(UUID.randomUUID().toString(), clock.instant());
    }
}
