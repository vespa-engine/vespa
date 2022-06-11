// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.RevisionId;
import com.yahoo.vespa.hosted.controller.application.Endpoint;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Serializes config for integration tests against Vespa deployments.
 *
 * @author jonmv
 */
public class TestConfigSerializer {

    private final SystemName system;

    public TestConfigSerializer(SystemName system) {
        this.system = system;
    }

    public Slime configSlime(ApplicationId id,
                             JobType type,
                             boolean isCI,
                             Version platform,
                             RevisionId revision,
                             Instant deployedAt,
                             Map<ZoneId, List<Endpoint>> deployments,
                             Map<ZoneId, List<String>> clusters) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setString("application", id.serializedForm());
        root.setString("zone", type.zone().value());
        root.setString("system", system.value());
        root.setBool("isCI", isCI);
        root.setString("platform", platform.toFullString());
        root.setLong("revision", revision.number());
        root.setLong("deployedAt", deployedAt.toEpochMilli());

        // TODO jvenstad: remove when clients can be updated
        Cursor endpointsObject = root.setObject("endpoints");
        deployments.forEach((zone, endpoints) -> {
            Cursor endpointArray = endpointsObject.setArray(zone.value());
            for (Endpoint endpoint : endpoints)
                endpointArray.addString(endpoint.url().toString());
        });

        Cursor zoneEndpointsObject = root.setObject("zoneEndpoints");
        deployments.forEach((zone, endpoints) -> {
            Cursor clusterEndpointsObject = zoneEndpointsObject.setObject(zone.value());
            for (Endpoint endpoint : endpoints)
                clusterEndpointsObject.setString(endpoint.name(), endpoint.url().toString());
        });

        if ( ! clusters.isEmpty()) {
            Cursor clustersObject = root.setObject("clusters");
            clusters.forEach((zone, clusterList) -> {
                Cursor clusterArray = clustersObject.setArray(zone.value());
                for (String cluster : clusterList)
                    clusterArray.addString(cluster);
            });
        }

        return slime;
    }

    /** Returns the config for the tests to run for the given job. */
    public byte[] configJson(ApplicationId id,
                             JobType type,
                             boolean isCI,
                             Version platform,
                             RevisionId revision,
                             Instant deployedAt,
                             Map<ZoneId, List<Endpoint>> deployments,
                             Map<ZoneId, List<String>> clusters) {
        try {
            return SlimeUtils.toJsonBytes(configSlime(id, type, isCI, platform, revision, deployedAt, deployments, clusters));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
