// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

import java.util.Objects;
import java.util.Optional;

/**
 * TODO: this class can probably be moved to a non-PublicApi package
 * TODO Vespa 9: remove
 *
 * @author olaa
 */
public class TenantSecretStore {

    private final String name;
    private final String awsId;
    private final String role;
    private final Optional<String> externalId;

    public TenantSecretStore(String name, String awsId, String role) {
        this(name, awsId, role, Optional.empty());
    }

    public TenantSecretStore(String name, String awsId, String role, String externalId) {
        this(name, awsId, role, Optional.of(externalId));
    }

    public TenantSecretStore(String name, String awsId, String role, Optional<String> externalId) {
        this.name = name;
        this.awsId = awsId;
        this.role = role;
        this.externalId = externalId;
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

    public Optional<String> getExternalId() {
        return externalId;
    }

    public TenantSecretStore withExternalId(String externalId) {
        return new TenantSecretStore(name, awsId, role, Optional.of(externalId));
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
                ", externalId='" + (externalId.orElse("<EMPTY>") + '\'') +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantSecretStore that = (TenantSecretStore) o;
        return Objects.equals(name, that.name) && Objects.equals(awsId, that.awsId) && Objects.equals(role, that.role) && Objects.equals(externalId, that.externalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, awsId, role, externalId);
    }

}
