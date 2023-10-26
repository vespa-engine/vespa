// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterSpec;

import java.util.Optional;

/**
 * API of infrastructure application that is accessible via DuperModelInfraApi.
 *
 * @author hakonhall
 */
public interface InfraApplicationApi {

    ApplicationId getApplicationId();
    Capacity getCapacity();
    ClusterSpec getClusterSpecWithVersion(Version version);

}
