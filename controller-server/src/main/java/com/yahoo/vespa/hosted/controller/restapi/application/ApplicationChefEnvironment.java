// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.TenantName;

/**
 * Represents Chef environments for applications/deployments. Used for promotion of Chef environments
 *
 * @author mortent
 */
public class ApplicationChefEnvironment {

    private final String systemChefEnvironment;
    private final String systemSuffix;

    public ApplicationChefEnvironment(SystemName system) {
        if (system == SystemName.main) {
            systemChefEnvironment = "hosted-verified-prod";
            systemSuffix = "";
        } else {
            systemChefEnvironment = "hosted-infra-cd";
            systemSuffix = "-cd";
        }
    }

    public String systemChefEnvironment() {
        return systemChefEnvironment;
    }

    public String applicationSourceEnvironment(TenantName tenantName, ApplicationName applicationName) {
        // placeholder and component already used in legacy chef promotion
        return String.format("hosted-instance%s_%s_%s_placeholder_component_default", systemSuffix, tenantName, applicationName);
    }

    public String applicationTargetEnvironment(TenantName tenantName, ApplicationName applicationName, Environment environment, RegionName regionName) {
        return String.format("hosted-instance%s_%s_%s_%s_%s_default", systemSuffix, tenantName, applicationName, regionName, environment);
    }

}
