// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.resource;

import java.util.Collection;
import java.util.List;

/**
 * Consumes and retrieves snapshots of resources allocated per application.
 *
 * @author olaa
 */
public interface MeteringClient {

    void consume(Collection<ResourceSnapshot> resources);

    MeteringInfo getResourceSnapshots(String tenantName, String applicationName);

}
