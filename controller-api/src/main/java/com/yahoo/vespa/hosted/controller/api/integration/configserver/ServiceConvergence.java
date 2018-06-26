// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

/**
 * Service convergence status for an application.
 *
 * @author mpolden
 */
public class ServiceConvergence {

    private final ApplicationId application;
    private final ZoneId zone;
    private final boolean converged;

    public ServiceConvergence(ApplicationId application, ZoneId zone, boolean converged) {
        this.application = application;
        this.zone = zone;
        this.converged = converged;
    }

    public ApplicationId application() {
        return application;
    }

    public ZoneId zone() {
        return zone;
    }

    public boolean converged() {
        return converged;
    }
}
