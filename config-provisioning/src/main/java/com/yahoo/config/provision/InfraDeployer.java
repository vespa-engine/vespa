// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Optional;

/**
 * A deployer is used to deploy infrastructure applications.
 *
 * @author freva
 */
public interface InfraDeployer {

    /**
     * @param application the infrastructure application to be deployed
     * @return empty if the given application is not an infrastructure application or a {@link Deployment}
     */
    Optional<Deployment> getDeployment(ApplicationId application);

    /** Deploys all supported infrastructure applications in this zone. */
    void activateAllSupportedInfraApplications(boolean propagateException);

}
