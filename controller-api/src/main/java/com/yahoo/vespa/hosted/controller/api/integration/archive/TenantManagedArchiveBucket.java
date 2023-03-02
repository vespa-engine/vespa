// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.archive;

import com.yahoo.config.provision.CloudAccount;

import java.time.Instant;

/**
 * Represents a cloud storage bucket (e.g. AWS S3 or Google Storage) used to store archive data - logs, heap/core dumps, etc.
 * that is managed by the tenant directly.
 *
 * @author freva
 */
public record TenantManagedArchiveBucket(String bucketName, CloudAccount cloudAccount, Instant updatedAt) {
}
