// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.HashMap;
import java.util.Map;

/**
 * @author olaa
 */
public class MockResourceTagger implements ResourceTagger {

    Map<ZoneId, Map<HostName, ApplicationId>> values = new HashMap<>();

    @Override
    public int tagResources(ZoneApi zone, Map<HostName, ApplicationId> tenantOfHosts) {
        values.put(zone.getId(), tenantOfHosts);
        return 0;
    }

    public Map<ZoneId, Map<HostName, ApplicationId>> getValues() {
        return values;
    }
}
