// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import org.junit.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class RoutingPolicyTest {

    @Test
    public void test_endpoint_names() {
        ZoneId zoneId = ZoneId.from("prod", "us-north-1");
        ApplicationId withInstance = ApplicationId.from("tenant", "application", "instance");
        testAlias("instance--application--tenant.prod.us-north-1.vespa.oath.cloud", "default", withInstance, zoneId);
        testAlias("cluster--instance--application--tenant.prod.us-north-1.vespa.oath.cloud", "cluster", withInstance, zoneId);

        ApplicationId withDefaultInstance = ApplicationId.from("tenant", "application", "default");
        testAlias("application--tenant.prod.us-north-1.vespa.oath.cloud", "default", withDefaultInstance, zoneId);
        testAlias("cluster--application--tenant.prod.us-north-1.vespa.oath.cloud", "cluster", withDefaultInstance, zoneId);
    }

    private void testAlias(String expected, String clusterName, ApplicationId applicationId, ZoneId zoneId) {
        assertEquals(expected, new RoutingPolicy(applicationId, zoneId, ClusterSpec.Id.from(clusterName),
                                                 HostName.from("lb-0"), Optional.empty(), Set.of()).alias().value());
    }

}
