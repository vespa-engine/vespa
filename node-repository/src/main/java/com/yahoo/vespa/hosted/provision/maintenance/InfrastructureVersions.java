// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.component.Vtag;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Multithread safe class to see and set target versions for infrastructure node types.
 * {@link InfrastructureProvisioner} maintainer will then allocate the nodes of given node type
 * with a wanted version equal to the given target version.
 *
 * @author freva
 */
public class InfrastructureVersions {

    private static final Logger logger = Logger.getLogger(InfrastructureVersions.class.getName());

    private final CuratorDatabaseClient db;
    private final Version defaultVersion;

    public InfrastructureVersions(CuratorDatabaseClient db) {
        this(db, Vtag.currentVersion);
    }

    InfrastructureVersions(CuratorDatabaseClient db, Version defaultVersion) {
        this.db = db;
        this.defaultVersion = defaultVersion;
    }

    public void setTargetVersion(NodeType nodeType, Version newTargetVersion, boolean force) {
        assertLegalNodeTypeForTargetVersion(nodeType);

        try (Lock lock = db.lockInfrastructureVersions()) {
            Map<NodeType, Version> infrastructureVersions = db.readInfrastructureVersions();
            Version currentTargetVersion = Optional.ofNullable(infrastructureVersions.get(nodeType))
                    .orElse(Version.emptyVersion);

            // Trying to set the version to the current version, skip
            if (currentTargetVersion.equals(newTargetVersion)) return;

            // If we don't force the set, we must set the new version to higher than the already set version
            if (!force && currentTargetVersion.isAfter(newTargetVersion)) {
                throw new IllegalArgumentException(String.format("Cannot downgrade version without setting 'force'. Current target version: %s, attempted to set target version: %s",
                        currentTargetVersion.toFullString(), newTargetVersion.toFullString()));
            }

            if (newTargetVersion.isEmpty()) {
                infrastructureVersions.remove(nodeType);
                logger.info("Removing target version for " + nodeType);
            } else {
                infrastructureVersions.put(nodeType, newTargetVersion);
                logger.info("Setting target version for " + nodeType + " to " + newTargetVersion.toFullString());
            }
            db.writeInfrastructureVersions(infrastructureVersions);
        }
    }

    public Version getTargetVersionFor(NodeType nodeType) {
        assertLegalNodeTypeForTargetVersion(nodeType);

        return Optional.ofNullable(db.readInfrastructureVersions().get(nodeType)).orElseGet(() -> {
            // Target version has never been set for this node type, set it to the default version of this server.
            // We need to set the version (in ZK) to prevent another config server from returning a different version.
            // No lock needed since this is only an issue in bootstrap case and in a potential race, all versions
            // are equally valid
            setTargetVersion(nodeType, defaultVersion, false);
            return defaultVersion;
        });
    }

    public Map<NodeType, Version> getTargetVersions() {
        return Collections.unmodifiableMap(db.readInfrastructureVersions());
    }

    private static void assertLegalNodeTypeForTargetVersion(NodeType nodeType) {
        switch (nodeType) {
            case config:
            case confighost:
            case controller:
            case controllerhost:
            case proxyhost:
            case host:
                break;
            default:
                throw new IllegalArgumentException("Target version for type " + nodeType + " is not allowed");
        }
    }
}
