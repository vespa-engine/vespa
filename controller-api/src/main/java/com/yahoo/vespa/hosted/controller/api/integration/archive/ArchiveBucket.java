// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.vespa.hosted.controller.api.identifiers.TenantId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Represents an S3 bucket used to store archive data - logs, heap/core dumps, etc.
 * @author andreer
 */
public class ArchiveBucket {
    private final String name;
    private final Optional<String> keyArn;
    private final Set<TenantId> tenants;

    public ArchiveBucket(String name, Optional<String> keyArn, Set<TenantId> tenants) {
        this.name = name;
        this.keyArn = keyArn;
        this.tenants = tenants;
    }

    public String bucketArn() {
        return name;
    }

    public Optional<String> keyArn() {
        return keyArn;
    }

    public Set<TenantId> tenants() {
        return tenants;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArchiveBucket that = (ArchiveBucket) o;
        return name.equals(that.name) && keyArn.equals(that.keyArn) && tenants.equals(that.tenants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, keyArn, tenants);
    }
}