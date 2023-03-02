// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.google.common.collect.Sets;
import com.yahoo.config.provision.TenantName;

import java.util.Objects;
import java.util.Set;

/**
 * Represents a cloud storage bucket (e.g. AWS S3 or Google Storage) used to store archive data - logs, heap/core dumps, etc.
 * that is managed by the Vespa controller.
 *
 * @author andreer
 */
public class VespaManagedArchiveBucket {
    private final String bucketName;
    private final String keyArn;
    private final Set<TenantName> tenants;

    public VespaManagedArchiveBucket(String bucketName, String keyArn) {
        this(bucketName, keyArn, Set.of());
    }

    private VespaManagedArchiveBucket(String bucketName, String keyArn, Set<TenantName> tenants) {
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

    public VespaManagedArchiveBucket withTenant(TenantName tenant) {
        return withTenants(Set.of(tenant));
    }

    public VespaManagedArchiveBucket withTenants(Set<TenantName> tenants) {
        return new VespaManagedArchiveBucket(bucketName, keyArn, Sets.union(this.tenants, tenants));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VespaManagedArchiveBucket that = (VespaManagedArchiveBucket) o;
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