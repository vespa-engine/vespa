// Copyright 2021 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.archive;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucket;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveBucketDb;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveService;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * This class decides which tenant goes in what bucket, and creates new buckets when required.
 *
 * @author andreer
 */
public class CuratorArchiveBucketDb implements ArchiveBucketDb {

    /**
     * Due to policy limits, we can't put data for more than this many tenants in a bucket.
     * Policy size limit is 20kb, with approx. 500 bytes of policy required per tenant = 40 tenants.
     * We set the maximum a bit lower to have a solid margin of error.
     */
    private final static int TENANTS_PER_BUCKET = 30;

    private final ArchiveService archiveService;
    private final CuratorDb curatorDb;
    private final StringFlag bucketNameFlag;

    @Inject
    public CuratorArchiveBucketDb(Controller controller) {
        this.archiveService = controller.serviceRegistry().archiveService();
        this.curatorDb = controller.curator();
        this.bucketNameFlag = Flags.SYNC_HOST_LOGS_TO_S3_BUCKET.bindTo(controller.flagSource());
    }

    @Override
    public Optional<URI> archiveUriFor(ZoneId zoneId, TenantName tenant) {
        String bucketArn = bucketNameFlag
                .with(FetchVector.Dimension.ZONE_ID, zoneId.value())
                .with(FetchVector.Dimension.TENANT_ID, tenant.value())
                .value();

        if (bucketArn.isBlank()) return Optional.empty();

        if ("auto".equals(bucketArn)) bucketArn = findOrAssignBucket(zoneId, tenant);

        return Optional.of(URI.create(String.format("s3://%s/%s/", bucketArn, tenant.value())));
    }

    private String findOrAssignBucket(ZoneId zoneId, TenantName tenant) {
        var zoneBuckets = curatorDb.readArchiveBuckets(zoneId);
        if (find(tenant, zoneBuckets).isPresent()) return find(tenant, zoneBuckets).get().bucketArn();
        else return assignToBucket(zoneId, tenant);
    }

    private String assignToBucket(ZoneId zoneId, TenantName tenant) {
        try (var lock = curatorDb.lockArchiveBuckets(zoneId)) {
            Set<ArchiveBucket> zoneBuckets = new HashSet<>(curatorDb.readArchiveBuckets(zoneId));

            // Some other thread might have assigned it before we grabbed the lock
            if (find(tenant, zoneBuckets).isPresent()) return find(tenant, zoneBuckets).get().bucketArn();

            // If not, find an existing bucket with space
            Optional<ArchiveBucket> unfilledBucket = zoneBuckets.stream()
                    .filter(bucket -> bucket.tenants().size() < TENANTS_PER_BUCKET)
                    .findAny();

            // And place the tenant in that bucket.
            if (unfilledBucket.isPresent()) {
                var unfilled = unfilledBucket.get();

                zoneBuckets.remove(unfilled);
                zoneBuckets.add(unfilled.withTenant(tenant));
                curatorDb.writeArchiveBuckets(zoneId, zoneBuckets);

                return unfilled.bucketArn();
            }

            // We'll have to create a new bucket
            var newBucket = archiveService.createArchiveBucketFor(zoneId, Set.of(tenant));
            zoneBuckets.add(newBucket);
            curatorDb.writeArchiveBuckets(zoneId, zoneBuckets);
            return newBucket.bucketArn();
        }
    }

    @NotNull
    private Optional<ArchiveBucket> find(TenantName tenant, Set<ArchiveBucket> zoneBuckets) {
        return zoneBuckets.stream().filter(bucket -> bucket.tenants().contains(tenant)).findAny();
    }

    @Override
    public Set<ArchiveBucket> buckets(ZoneId zoneId) {
        return curatorDb.readArchiveBuckets(zoneId);
    }
}
