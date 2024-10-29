// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.os;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.yahoo.component.Version;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.CloudName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDb;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.yolean.Exceptions;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe class that manages an OS version change for nodes in this repository. An {@link OsUpgrader} decides how a
 * {@link OsVersionTarget} is applied to nodes.
 *
 * A version target is initially inactive. Activation decision is taken by
 * {@link com.yahoo.vespa.hosted.provision.maintenance.OsUpgradeActivator}.
 *
 * The target OS version for each node type is set through the /nodes/v2/upgrade REST API.
 *
 * @author mpolden
 */
public class OsVersions {

    private static final Logger LOG = Logger.getLogger(OsVersions.class.getName());

    private final NodeRepository nodeRepository;
    private final CuratorDb db;
    private final Cloud cloud;
    private final Optional<HostProvisioner> hostProvisioner;
    // Version is queried for each host to upgrade, so we cache the results for a while to avoid excessive
    // API calls to the host provisioner
    private final Cache<CloudAccount, Set<Version>> availableVersions = CacheBuilder.newBuilder()
                                                                                    .expireAfterWrite(10, TimeUnit.MINUTES)
                                                                                    .build();

    private final BooleanFlag snapshotsEnabled;

    public OsVersions(NodeRepository nodeRepository, Optional<HostProvisioner> hostProvisioner) {
        this(nodeRepository, nodeRepository.zone().cloud(), hostProvisioner);
    }

    OsVersions(NodeRepository nodeRepository, Cloud cloud, Optional<HostProvisioner> hostProvisioner) {
        this.nodeRepository = Objects.requireNonNull(nodeRepository);
        this.db = nodeRepository.database();
        this.cloud = Objects.requireNonNull(cloud);
        this.hostProvisioner = Objects.requireNonNull(hostProvisioner);
        this.snapshotsEnabled = Flags.SNAPSHOTS_ENABLED.bindTo(nodeRepository.flagSource());

        // Read and write all versions to make sure they are stored in the latest version of the serialized format
        try (var lock = db.lockOsVersionChange()) {
            db.writeOsVersionChange(db.readOsVersionChange());
        }
    }

    /** Returns the current OS version change */
    public OsVersionChange readChange() {
        return db.readOsVersionChange();
    }

    /** Write the current OS version change with the result of the given operation applied */
    public void writeChange(UnaryOperator<OsVersionChange> operation) {
        try (var lock = db.lockOsVersionChange()) {
            OsVersionChange change = readChange();
            OsVersionChange newChange = operation.apply(change);
            if (newChange.equals(change)) return; // Nothing changed
            db.writeOsVersionChange(newChange);
        }
    }

    /** Returns the current target version for given node type, if any */
    public Optional<Version> targetFor(NodeType type) {
        return Optional.ofNullable(readChange().targets().get(type)).map(OsVersionTarget::version);
    }

    /**
     * Remove OS target for given node type. Nodes of this type will stop receiving wanted OS version in their
     * node object.
     */
    public void removeTarget(NodeType nodeType) {
        require(nodeType);
        writeChange((change) -> {
            Version target = Optional.ofNullable(change.targets().get(nodeType))
                                     .map(OsVersionTarget::version)
                                     .orElse(Version.emptyVersion);
            chooseUpgrader(nodeType, Optional.of(target)).disableUpgrade(nodeType);
            return change.withoutTarget(nodeType);
        });
    }

    /** Set the target OS version for nodes of given type */
    public void setTarget(NodeType nodeType, Version version, boolean force) {
        require(nodeType);
        requireNonEmpty(version);
        writeChange((change) -> {
            Optional<OsVersionTarget> currentTarget = Optional.ofNullable(change.targets().get(nodeType));
            Optional<Version> currentVersion = currentTarget.map(OsVersionTarget::version);

            if (currentVersion.equals(Optional.of(version))) {
                return change; // Version unchanged: Nothing to do
            }

            if (!force && currentVersion.filter(v -> v.isAfter(version)).isPresent()) {
                throw new IllegalArgumentException("Cannot set target OS version to " + version.toFullString() +
                                                   " without setting 'force', as it's lower than the current version: "
                                                   + currentTarget.get().version().toFullString());
            }

            LOG.info("Set OS target version for " + nodeType + " nodes to " + version.toFullString());
            return change.withTarget(version, nodeType);
        });
    }

    /** Returns the versions available to given host */
    public Set<Version> availableTo(Node host, Version requestedVersion) {
        if (hostProvisioner.isEmpty()) {
            return Set.of(requestedVersion);
        }
        try {
            return availableVersions.get(host.cloudAccount(),
                                         () -> hostProvisioner.get().osVersions(host, requestedVersion.getMajor()));
        } catch (ExecutionException e) {
            LOG.log(Level.WARNING, "Failed to list supported OS versions in " + host.cloudAccount() + ": " + Exceptions.toMessageString(e));
            return Set.of();
        }
    }

    /** Invalidate cached versions. For testing purposes */
    void invalidate() {
        availableVersions.invalidateAll();
    }

    /** Resume or halt upgrade of given node type */
    public void resumeUpgradeOf(NodeType nodeType, boolean resume) {
        require(nodeType);
        try (Lock lock = db.lockOsVersionChange()) {
            OsVersionTarget target = readChange().targets().get(nodeType);
            if (target == null) return; // No target set for this type
            OsUpgrader upgrader = chooseUpgrader(nodeType, Optional.of(target.version()));
            if (resume) {
                upgrader.upgradeTo(target);
            } else {
                upgrader.disableUpgrade(nodeType);
            }
        }
    }

    /** Returns whether node is currently deferring its upgrade */
    public boolean deferringUpgrade(Node node) {
        return chooseUpgrader(node.type(), Optional.empty()).deferringUpgrade(node, nodeRepository.clock().instant());
    }

    /** Returns the upgrader to use when upgrading given node type to target */
    private OsUpgrader chooseUpgrader(NodeType nodeType, Optional<Version> target) {
        if (cloud.dynamicProvisioning()) {
            boolean canRebuild = cloud.name().equals(CloudName.AWS);
            boolean canSnapshot = snapshotsEnabled.value();
            if (canRebuild) {
                if (canSnapshot) {
                    // When snapshots are supported, we can rebuild any host
                    return new RebuildingOsUpgrader(nodeRepository, true, false);
                } else {
                    // Otherwise we need a combination of rebuilding (remote disks) and retiring (local disks)
                    return new CompositeOsUpgrader(nodeRepository,
                                                   List.of(new RebuildingOsUpgrader(nodeRepository, true, true),
                                                           new RetiringOsUpgrader(nodeRepository, true)));
                }
            }
            // Rebuild unsupported. Retire hosts regardless of storage type
            return new RetiringOsUpgrader(nodeRepository, false);
        }
        // Require rebuild if we have any nodes of this type on a major version lower than target
        boolean rebuildRequired = target.isPresent() &&
                                  nodeRepository.nodes().list(Node.State.active).nodeType(nodeType).stream()
                                                .map(Node::status)
                                                .map(Status::osVersion)
                                                .anyMatch(osVersion -> osVersion.current().isPresent() &&
                                                                       osVersion.current().get().getMajor() < target.get().getMajor());
        if (rebuildRequired) {
            return new RebuildingOsUpgrader(nodeRepository, false, false);
        }
        return new DelegatingOsUpgrader(nodeRepository);
    }

    private static void requireNonEmpty(Version version) {
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Invalid target version: " + version.toFullString());
        }
    }

    private static void require(NodeType nodeType) {
        if (!nodeType.isHost()) {
            throw new IllegalArgumentException("Node type '" + nodeType + "' does not support OS upgrades");
        }
    }

}
