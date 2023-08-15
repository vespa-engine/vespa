// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.model.VespaModel;

import java.util.logging.Logger;

/**
 * Validator to check that only infrastructure tenant can use non-default application-type
 *
 * @author mortent
 */
public class InfrastructureDeploymentValidator extends Validator {

    private static final Logger log = Logger.getLogger(InfrastructureDeploymentValidator.class.getName());

    @Override
    public void validate(VespaModel model, DeployState deployState) {
        // Allow the internally defined tenant owning all infrastructure applications
        if (ApplicationId.global().tenant().equals(model.applicationPackage().getApplicationId().tenant())) return;
        ConfigModelContext.ApplicationType applicationType = model.getAdmin().getApplicationType();
        if (applicationType != ConfigModelContext.ApplicationType.DEFAULT) {
            log.warning("Tenant %s is not allowed to use application type %s".formatted(model.applicationPackage().getApplicationId().toFullString(), applicationType));
            throw new IllegalArgumentException("Tenant is not allowed to override application type");
        }
    }
}
