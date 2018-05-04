package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
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

    private static Logger logger = Logger.getLogger(InfrastructureVersions.class.getName());

    private final CuratorDatabaseClient db;

    public InfrastructureVersions(CuratorDatabaseClient db) {
        this.db = db;
    }

    public void setTargetVersion(NodeType nodeType, Version newTargetVersion, boolean force) {
        if (nodeType != NodeType.config && nodeType != NodeType.confighost && nodeType != NodeType.proxyhost) {
            throw new IllegalArgumentException("Cannot set version for type " + nodeType);
        }

        try (Lock lock = db.lockInfrastructureVersions()) {
            Map<NodeType, Version> infrastructureVersions = db.readInfrastructureVersions();
            Optional<Version> currentTargetVersion = Optional.ofNullable(infrastructureVersions.get(nodeType));

            // Trying to set the version to the current version, skip
            if (currentTargetVersion.equals(Optional.of(newTargetVersion))) return;

            // If we don't force the set, we must set the new version to higher than the already set version
            if (!force && currentTargetVersion.isPresent()) {
                if (currentTargetVersion.get().isAfter(newTargetVersion))
                    throw new IllegalArgumentException(String.format("Cannot downgrade version without setting 'force'. " +
                            "Current target version: %s, attempted to set target version: %s",
                            currentTargetVersion.get().toFullString(), newTargetVersion.toFullString()));
            }

            infrastructureVersions.put(nodeType, newTargetVersion);
            db.writeInfrastructureVersions(infrastructureVersions);
            logger.info("Set target version for " + nodeType + " to " + newTargetVersion.toFullString());
        }
    }

    public Optional<Version> getTargetVersionFor(NodeType nodeType) {
        return Optional.ofNullable(db.readInfrastructureVersions().get(nodeType));
    }

    public Map<NodeType, Version> getTargetVersions() {
        return Collections.unmodifiableMap(db.readInfrastructureVersions());
    }
}
