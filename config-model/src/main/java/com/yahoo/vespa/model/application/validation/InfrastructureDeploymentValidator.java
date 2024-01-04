// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.provision.TenantName;
import com.yahoo.vespa.model.application.validation.Validation.Context;

import java.util.logging.Logger;

/**
 * Validator to check that only infrastructure tenant can use non-default application-type
 *
 * @author mortent
 */
public class InfrastructureDeploymentValidator implements Validator {

    private static final Logger log = Logger.getLogger(InfrastructureDeploymentValidator.class.getName());

    @Override
    public void validate(Context context) {
        // Allow the internally defined tenant owning all infrastructure applications
        if (TenantName.from("hosted-vespa").equals(context.model().applicationPackage().getApplicationId().tenant())) return;
        ConfigModelContext.ApplicationType applicationType = context.model().getAdmin().getApplicationType();
        if (applicationType != ConfigModelContext.ApplicationType.DEFAULT) {
            log.warning("Tenant %s is not allowed to use application type %s".formatted(context.model().applicationPackage().getApplicationId().toFullString(), applicationType));
            context.illegal("Tenant is not allowed to override application type");
        }
    }

}
