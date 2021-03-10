// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A deployer is used to deploy applications.
 *
 * @author bratseth
 */
public interface Deployer {

    /**
     * Creates a new deployment from the active application, if available. Will use the default timeout for deployment.
     *
     * @param application the active application to be redeployed
     * @return a new deployment from the local active, or empty if a local active application
     *         was not present for this id (meaning it either is not active or deployed at another
     *         node in the config server cluster)
     */
    default Optional<Deployment> deployFromLocalActive(ApplicationId application) {
        return deployFromLocalActive(application, false);
    }

    /**
     * Creates a new deployment from the active application, if available. Will use the default timeout for deployment.
     *
     * @param application the active application to be redeployed
     * @param bootstrap the deployment is done when bootstrapping
     * @return a new deployment from the local active, or empty if a local active application
     *         was not present for this id (meaning it either is not active or deployed at another
     *         node in the config server cluster)
     */
    Optional<Deployment> deployFromLocalActive(ApplicationId application, boolean bootstrap);

    /**
     * Creates a new deployment from the active application, if available. Prefer {@link #deployFromLocalActive(ApplicationId)}
     * if possible, this method is for testing and will override the default timeout for deployment.
     *
     * @param application the active application to be redeployed
     * @param timeout the timeout to use for each individual deployment operation
     * @return a new deployment from the local active, or empty if a local active application
     *         was not present for this id (meaning it either is not active or active on another
     *         node in the config server cluster)
     */
    default Optional<Deployment> deployFromLocalActive(ApplicationId application, Duration timeout) {
        return deployFromLocalActive(application, timeout, false);
    }

    /**
     * Creates a new deployment from the active application, if available. Prefer {@link #deployFromLocalActive(ApplicationId)}
     * if possible, this method is for testing and will override the default timeout for deployment.
     *
     * @param application the active application to be redeployed
     * @param timeout the timeout to use for each individual deployment operation
     * @param bootstrap the deployment is done when bootstrapping
     * @return a new deployment from the local active, or empty if a local active application
     *         was not present for this id (meaning it either is not active or active on another
     *         node in the config server cluster)
     */
    Optional<Deployment> deployFromLocalActive(ApplicationId application, Duration timeout, boolean bootstrap);

    /** Returns the time the current local active session was created, or empty if there is no local active session */
    Optional<Instant> lastDeployTime(ApplicationId application);

    /** Whether the deployer is bootstrapping, some users of the deployer will want to hold off with deployments in that case. */
    default boolean bootstrapping() { return false; }

    /** Timeout in server, clients can use this to set correct client timeout */
    default Duration serverDeployTimeout() { return Duration.ofMinutes(30); }

}
