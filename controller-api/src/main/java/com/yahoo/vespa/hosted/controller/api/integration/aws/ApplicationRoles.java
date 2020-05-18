// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.aws;

/**
 * @author mortent
 */
public class ApplicationRoles {
    private final String hostRole;
    private final String containerRole;

    public ApplicationRoles(String hostRole, String containerRole) {
        this.hostRole = hostRole;
        this.containerRole = containerRole;
    }

    public String hostRole() {
        return hostRole;
    }

    public String containerRole() {
        return containerRole;
    }
}
