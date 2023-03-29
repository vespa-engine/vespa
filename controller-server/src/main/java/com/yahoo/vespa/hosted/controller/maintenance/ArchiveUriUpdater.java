// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveUriUpdate;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ArchiveUris;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeRepository;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneRegistry;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.archive.CuratorArchiveBucketDb;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Updates archive URIs for tenants in all zones.
 *
 * @author freva
 */
public class ArchiveUriUpdater extends ControllerMaintainer {

    private static final Set<TenantName> INFRASTRUCTURE_TENANTS = Set.of(SystemApplication.TENANT);

    private final ApplicationController applications;
    private final NodeRepository nodeRepository;
    private final CuratorArchiveBucketDb archiveBucketDb;
    private final ZoneRegistry zoneRegistry;

    public ArchiveUriUpdater(Controller controller, Duration interval) {
        super(controller, interval);
        this.applications = controller.applications();
        this.nodeRepository = controller.serviceRegistry().configServer().nodeRepository();
        this.archiveBucketDb = controller.archiveBucketDb();
        this.zoneRegistry = controller.zoneRegistry();
    }

    @Override
    protected double maintain() {
        Map<ZoneId, Set<TenantName>> tenantsByZone = new HashMap<>();
        Map<ZoneId, Set<CloudAccount>> accountsByZone = new HashMap<>();

        controller().zoneRegistry().zonesIncludingSystem().reachable().zones().forEach(zone -> {
            tenantsByZone.put(zone.getVirtualId(), new HashSet<>(INFRASTRUCTURE_TENANTS));
            accountsByZone.put(zone.getVirtualId(), new HashSet<>());
        });

        for (var application : applications.asList()) {
            for (var instance : application.instances().values()) {
                for (var deployment : instance.deployments().values()) {
                    if (zoneRegistry.isEnclave(deployment.cloudAccount())) accountsByZone.get(deployment.zone()).add(deployment.cloudAccount());
                    else tenantsByZone.get(deployment.zone()).add(instance.id().tenant());
                }
            }
        }

        int failures = 0;
        for (ZoneId zone : tenantsByZone.keySet()) {
            try {
                ArchiveUris zoneArchiveUris = nodeRepository.getArchiveUris(zone);

                Stream.of(
                        // Tenant URIs that need to be added or updated
                        tenantsByZone.get(zone).stream()
                                .flatMap(tenant -> archiveBucketDb.archiveUriFor(zone, tenant, true)
                                        .filter(uri -> !uri.equals(zoneArchiveUris.tenantArchiveUris().get(tenant)))
                                        .map(uri -> ArchiveUriUpdate.setArchiveUriFor(tenant, uri))
                                        .stream()),
                        // Account URIs that need to be added or updated
                        accountsByZone.get(zone).stream()
                                .flatMap(account -> archiveBucketDb.archiveUriFor(zone, account, true)
                                        .filter(uri -> !uri.equals(zoneArchiveUris.accountArchiveUris().get(account)))
                                        .map(uri -> ArchiveUriUpdate.setArchiveUriFor(account, uri))
                                        .stream()),
                        // Tenant URIs that need to be deleted
                        zoneArchiveUris.tenantArchiveUris().keySet().stream()
                                .filter(tenant -> !tenantsByZone.get(zone).contains(tenant))
                                .map(ArchiveUriUpdate::deleteArchiveUriFor),
                        // Account URIs that need to be deleted
                        zoneArchiveUris.accountArchiveUris().keySet().stream()
                                .filter(account -> !accountsByZone.get(zone).contains(account))
                                .map(ArchiveUriUpdate::deleteArchiveUriFor))
                        .flatMap(s -> s)
                        .forEach(update -> nodeRepository.updateArchiveUri(zone, update));
            } catch (Exception e) {
                log.log(Level.WARNING, "Failed to update archive URI in " + zone + ". Retrying in " + interval() + ". Error: " +
                        Exceptions.toMessageString(e));
                failures++;
            }
        }

        return asSuccessFactorDeviation(tenantsByZone.size(), failures);
    }

}
