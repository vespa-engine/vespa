// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags.custom;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * A Vespa service that can be continuously profiled, as named in the {@code pyroscope-profiling}
 * feature flag (see {@link com.yahoo.vespa.flags.PermanentFlags#PYROSCOPE_PROFILING}).
 *
 * <p>The enum names are the vocabulary used when setting the flag, and are deliberately the names
 * people use for these services. The service type is what Vespa itself calls them: config-sentinel
 * stamps it into every service's environment as {@code VESPA_SERVICE_NAME}, which is how a running
 * process is matched back to one of these. Notably proton's service type is {@code searchnode}.</p>
 *
 * @author onur
 */
public enum ProfiledService {

    /** The application container, i.e. the JVM running the tenant's components. */
    CONTAINER("container"),

    /** proton, the search core. Vespa names this service 'searchnode'. */
    PROTON("searchnode"),

    /** The distributor. */
    DISTRIBUTOR("distributor"),

    /** The metrics proxy. */
    METRICS_PROXY("metricsproxy-container");

    private final String serviceType;

    ProfiledService(String serviceType) {
        this.serviceType = serviceType;
    }

    /** The value config-sentinel sets as VESPA_SERVICE_NAME, without any instance number. */
    public String serviceType() {
        return serviceType;
    }

    /** Returns the service with the given Vespa service type, if it is one that can be profiled. */
    public static Optional<ProfiledService> ofServiceType(String serviceType) {
        return Stream.of(values()).filter(service -> service.serviceType.equals(serviceType)).findAny();
    }

}
