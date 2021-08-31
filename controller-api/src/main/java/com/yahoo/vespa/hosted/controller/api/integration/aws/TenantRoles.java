// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

/**
 * @author mortent
 */
public class TenantRoles {
    private final String hostRole;
    private final String tenantHostServiceRole;
    private final String containerRole;

    public TenantRoles(String hostRole, String tenantHostServiceRole, String containerRole) {
        this.hostRole = hostRole;
        this.tenantHostServiceRole = tenantHostServiceRole;
        this.containerRole = containerRole;
    }

    public String hostRole() {
        return hostRole;
    }

    public String hostServiceRole() {
        return tenantHostServiceRole;
    }

    public String containerRole() {
        return containerRole;
    }
}
