// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * A deployment of an application
 *
 * @author bratseth
 */
public interface Deployment {

    /**
     * Prepares activation of this deployment.
     * This will do all validation and preparatory steps in the system such that a subsequent activation should
     * be fast and error free.
     */
    void prepare();

    /**
     * Activates this deployment. This will prepare it if necessary.
     *
     * @return the application config generation that became active by this invocation
     */
    long activate();

    /**
     * Request a restart of services of this application on hosts matching the filter.
     * This is sometimes needed after activation, but can also be requested without
     * doing prepare and activate in the same session.
     */
    void restart(HostFilter filter);

}
