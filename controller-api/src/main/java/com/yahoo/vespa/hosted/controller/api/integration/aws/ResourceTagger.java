// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneApi;

import java.util.Map;

/**
 * @author olaa
 */
public interface ResourceTagger {

    /**
     * Returns number of tagged resources
     */
    int tagResources(ZoneApi zone, Map<HostName, ApplicationId> ownerOfHosts);

    static ResourceTagger empty() {
        return (zone, tenantOfHosts) -> 0;
    }

}
