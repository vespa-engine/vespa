// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.secrets;

import java.util.Objects;

/**
 * @author olaa
 */
public class TenantSecretStore {

    private final String name;
    private final String awsId;
    private final String role;

    public TenantSecretStore(String name, String awsId, String role) {
        this.name = name;
        this.awsId = awsId;
        this.role = role;
    }

    public String getName() {
        return name;
    }

    public String getAwsId() {
        return awsId;
    }

    public String getRole() {
        return role;
    }

    public boolean isValid() {
        return !name.isBlank() &&
                !awsId.isBlank() &&
                !role.isBlank();
    }

    @Override
    public String toString() {
        return "TenantSecretStore{" +
                "name='" + name + '\'' +
                ", awsId='" + awsId + '\'' +
                ", role='" + role + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantSecretStore that = (TenantSecretStore) o;
        return name.equals(that.name) &&
                awsId.equals(that.awsId) &&
                role.equals(that.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, awsId, role);
    }
}
