// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import com.google.common.base.Strings;

/**
 * @author mortent
 */
public class ApplicationRoles {
    private final String applicationHostRole;
    private final String applicationContainerRole;

    public ApplicationRoles(String applicationHostRole, String applicationContainerRole) {
        this.applicationHostRole = applicationHostRole;
        this.applicationContainerRole = applicationContainerRole;
    }

    /**
     * @return an ApplicationRoles instance if both hostRole and containerRole is non-empty, <code>null</code> otherwise
     */
    public static ApplicationRoles fromString(String hostRole, String containerRole) {
        if(Strings.isNullOrEmpty(hostRole) || Strings.isNullOrEmpty(containerRole)) {
            return null;
        }
        return new ApplicationRoles(hostRole, containerRole);
    }

    public String applicationContainerRole() {
        return applicationContainerRole;
    }

    public String applicationHostRole() {
        return applicationHostRole;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ApplicationRoles that = (ApplicationRoles) o;
        if (!applicationHostRole.equals(that.applicationHostRole)) return false;
        return applicationContainerRole.equals(that.applicationContainerRole);
    }

    @Override
    public int hashCode() {
        int result = applicationHostRole.hashCode();
        result = 31 * result + applicationContainerRole.hashCode();
        return result;
    }
}
