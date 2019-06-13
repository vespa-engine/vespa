// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.organization;

import com.yahoo.config.provision.ApplicationId;

/**
 * @author olaa
 */
public interface BillingHandler {

    void handleBilling(ApplicationId applicationId, String customerId);
}
