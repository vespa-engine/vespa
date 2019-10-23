// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.TenantName;

import java.util.Collection;

/**
 * Consumes and retrieves snapshots of resources allocated per application.
 *
 * @author olaa
 */
public interface MeteringClient {

    void consume(Collection<ResourceSnapshot> resources);

    MeteringInfo getResourceSnapshots(TenantName tenantName, ApplicationName applicationName);

}
