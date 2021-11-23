// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.user;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.tenant.Tenant;

import java.util.List;

/**
 * @author olaa
 */
public interface RoleMaintainer {

    /** Given the set of all existing tenants and applications, delete any superflous roles */
    void deleteLeftoverRoles(List<Tenant> tenants, List<ApplicationId> applications);

    /** Finds the subset of tenants that should be deleted based on role/domain existence */
    List<Tenant> tenantsToDelete(List<Tenant> tenants);

}
