// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.google.common.collect.Sets;
import com.yahoo.config.provision.TenantName;

import java.util.Objects;
import java.util.Set;

/**
 * Represents an S3 bucket used to store archive data - logs, heap/core dumps, etc.
 *
 * @author andreer
 */
public class ArchiveBucket {
    private final String bucketName;
    private final String keyArn;
    private final Set<TenantName> tenants;

    public ArchiveBucket(String bucketName, String keyArn) {
        this(bucketName, keyArn, Set.of());
    }

    private ArchiveBucket(String bucketName, String keyArn, Set<TenantName> tenants) {
        this.bucketName = bucketName;
        this.keyArn = keyArn;
        this.tenants = Set.copyOf(tenants);
    }

    public String bucketName() {
        return bucketName;
    }

    public String keyArn() {
        return keyArn;
    }

    public Set<TenantName> tenants() {
        return tenants;
    }

    public ArchiveBucket withTenant(TenantName tenant) {
        return withTenants(Set.of(tenant));
    }

    public ArchiveBucket withTenants(Set<TenantName> tenants) {
        return new ArchiveBucket(bucketName, keyArn, Sets.union(this.tenants, tenants));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ArchiveBucket that = (ArchiveBucket) o;
        return bucketName.equals(that.bucketName) && keyArn.equals(that.keyArn) && tenants.equals(that.tenants);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bucketName, keyArn, tenants);
    }

    @Override
    public String toString() {
        return "ArchiveBucket{" +
                "bucketName='" + bucketName + '\'' +
                ", keyArn='" + keyArn + '\'' +
                ", tenants=" + tenants +
                '}';
    }
}