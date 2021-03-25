// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.TenantName;

import java.util.Objects;
import java.util.Set;

/**
 * Represents an S3 bucket used to store archive data - logs, heap/core dumps, etc.
 *
 * @author andreer
 */
public class ArchiveBucket {
    private final String bucketArn;
    private final String keyArn;
    private final Set<TenantName> tenants;

    public ArchiveBucket(String bucketArn, String keyArn, Set<TenantName> tenants) {
        this.bucketArn = bucketArn;
        this.keyArn = keyArn;
        this.tenants = tenants;
    }

    public String bucketArn() {
        return bucketArn;
    }

    public String keyArn() {
        return keyArn;
    }

    public Set<TenantName> tenants() {
        return tenants;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArchiveBucket that = (ArchiveBucket) o;
        return bucketArn.equals(that.bucketArn) && keyArn.equals(that.keyArn) && tenants.equals(that.tenants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketArn, keyArn, tenants);
    }
}