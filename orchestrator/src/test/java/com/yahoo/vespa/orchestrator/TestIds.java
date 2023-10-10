// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.applicationmodel.ApplicationInstanceId;
import com.yahoo.vespa.applicationmodel.ApplicationInstanceReference;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.applicationmodel.TenantId;

/**
 * @author Tony Vaagenes
 */
public class TestIds {
    public static final ApplicationInstanceReference APPLICATION_INSTANCE_REFERENCE =
            new ApplicationInstanceReference(
                    new TenantId("test-tenant"),
                    new ApplicationInstanceId("test-application:test-environment:test-region:test-instance-key"));

    public static final ApplicationInstanceReference APPLICATION_INSTANCE_REFERENCE2 =
            new ApplicationInstanceReference(
                    new TenantId("test-tenant2"),
                    new ApplicationInstanceId("test-application2:test-environment:test-region:test-instance-key"));

    public static final HostName HOST_NAME1 = new HostName("host1");
}
