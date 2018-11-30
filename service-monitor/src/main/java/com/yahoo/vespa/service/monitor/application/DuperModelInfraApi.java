// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;

import java.util.List;

/**
 * The DuperModel's API for infrastructure applications.
 *
 * @author hakonhall
 */
public interface DuperModelInfraApi {
    /** Returns the list of supported infrastructure applications. */
    List<InfraApplicationApi> getSupportedInfraApplications();

    /** Returns true if the DuperModel has registered the infrastructure application as active. */
    boolean infraApplicationIsActive(ApplicationId applicationId);

    /** Update the DuperModel: A supported infrastructure application has been activated or is active. */
    void infraApplicationActivated(ApplicationId applicationId, List<HostName> hostnames);

    /** Update the DuperModel: A supported infrastructure application has been removed or is not active. */
    void infraApplicationRemoved(ApplicationId applicationId);
}
