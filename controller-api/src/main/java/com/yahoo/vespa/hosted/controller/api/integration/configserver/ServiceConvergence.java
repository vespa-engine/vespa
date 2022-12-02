// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.ZoneId;

import java.util.List;

/**
 * Service convergence status for an application.
 *
 * @author mpolden
 * @author jonmv
 */
public class ServiceConvergence {

    private final ApplicationId application;
    private final ZoneId zone;
    private final boolean converged;
    private final long wantedGeneration;
    private final List<Status> services;

    public ServiceConvergence(ApplicationId application, ZoneId zone, boolean converged,
                              long wantedGeneration, List<Status> services) {
        this.application = application;
        this.zone = zone;
        this.converged = converged;
        this.wantedGeneration = wantedGeneration;
        this.services = ImmutableList.copyOf(services);
    }

    public ApplicationId application() { return application; }
    public ZoneId zone() { return zone; }
    public boolean converged() { return converged; }
    public long wantedGeneration() { return wantedGeneration; }
    public List<Status> services() { return services; }


    /** Immutable class detailing the config status of a particular service for an application. */
    public static class Status {
        private final HostName host;
        private final long port;
        private final String type;
        private final long currentGeneration;

        public Status(HostName host, long port, String type, long currentGeneration) {
            this.host = host;
            this.port = port;
            this.type = type;
            this.currentGeneration = currentGeneration;
        }

        public HostName host() { return host; }
        public long port() { return port; }
        public String type() { return type; }
        public long currentGeneration() { return currentGeneration; }

    }

}
