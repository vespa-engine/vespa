// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athenz;

import com.yahoo.config.provision.TenantName;

/**
 * @author olaa
 */
public class AthenzInstanceSynchronizerMock implements AthenzInstanceSynchronizer {
    @Override
    public void synchronizeInstances(TenantName tenant) {}
}
