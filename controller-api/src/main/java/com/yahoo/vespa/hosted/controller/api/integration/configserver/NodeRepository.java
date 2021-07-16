// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryNode;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeState;

import java.net.URI;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Node repository interface intended for use by the controller.
 *
 * @author mpolden
 */
public interface NodeRepository {

    void addNodes(ZoneId zone, Collection<NodeRepositoryNode> nodes);

    void deleteNode(ZoneId zone, String hostname);

    void setState(ZoneId zone, NodeState nodeState, String hostname);

    Node getNode(ZoneId zone, String hostname);

    /** List all nodes in given zone */
    List<Node> list(ZoneId zone, boolean includeDeprovisioned);

    /** List all nodes in zone having given hostnames */
    List<Node> list(ZoneId zone, List<HostName> hostnames);

    /** List all nodes in zone owned by given application */
    List<Node> list(ZoneId zone, ApplicationId application);

    /** List all nodes in states, in zone owned by given application */
    default List<Node> list(ZoneId zone, ApplicationId application, Set<Node.State> states) {
        return list(zone, application).stream()
                                      .filter(node -> states.contains(node.state()))
                                      .collect(Collectors.toList());
    }

    Application getApplication(ZoneId zone, ApplicationId application);

    void patchApplication(ZoneId zone, ApplicationId application,
                          double currentReadShare, double maxReadShare);

    NodeRepoStats getStats(ZoneId zone);

    Map<TenantName, URI> getArchiveUris(ZoneId zone);

    void setArchiveUri(ZoneId zone, TenantName tenantName, URI archiveUri);

    void removeArchiveUri(ZoneId zone, TenantName tenantName);

    /** Upgrade all nodes of given type to a new version */
    void upgrade(ZoneId zone, NodeType type, Version version);

    /** Upgrade OS for all nodes of given type to a new version */
    void upgradeOs(ZoneId zone, NodeType type, Version version, Optional<Duration> upgradeBudget);

    /** Get target versions for upgrades in given zone */
    TargetVersions targetVersionsOf(ZoneId zone);

    /** Requests firmware checks on all hosts in the given zone. */
    void requestFirmwareCheck(ZoneId zone);

    /** Cancels firmware checks on all hosts in the given zone. */
    void cancelFirmwareCheck(ZoneId zone);

    void retireAndDeprovision(ZoneId zoneId, String hostName);

    void patchNode(ZoneId zoneId, String hostName, NodeRepositoryNode node);

    void reboot(ZoneId zoneId, String hostName);

    /** Checks whether the zone has the spare capacity to remove the given hosts */
    boolean isReplaceable(ZoneId zoneId, List<HostName> hostNames);

}
