// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

import com.yahoo.config.provision.ApplicationId;

import java.util.Optional;

/**
 * @author mortent
 */
public interface ApplicationRoleService {
    Optional<ApplicationRoles> createApplicationRoles(ApplicationId applicationId);
}
