// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.api.integration.archive.ArchiveUriUpdate;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.ApplicationPatch;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Node repository interface intended for use by the controller.
 *
 * @author mpolden
 */
public interface NodeRepository {

    /** Add new nodes to the node repository */
    void addNodes(ZoneId zone, List<Node> nodes);

    /** Delete node */
    void deleteNode(ZoneId zone, String hostname);

    /** Move node to given state */
    void setState(ZoneId zone, Node.State state, String hostname);

    /** Get node from zone */
    Node getNode(ZoneId zone, String hostname);

    /** List nodes in given zone matching given filter */
    List<Node> list(ZoneId zone, NodeFilter filter);

    /** Get node repository's view of given application */
    Application getApplication(ZoneId zone, ApplicationId application);

    /** Update application */
    void patchApplication(ZoneId zone, ApplicationId application, ApplicationPatch patch);

    /** Get node statistics such as cost and load from given zone */
    NodeRepoStats getStats(ZoneId zone);

    /** Get all archive URIs found in zone */
    ArchiveUris getArchiveUris(ZoneId zone);

    /** Update some archive URI in the given zone */
    void updateArchiveUri(ZoneId zone, ArchiveUriUpdate archiveUriUpdate);

    /** Upgrade all nodes of given type to a new version */
    void upgrade(ZoneId zone, NodeType type, Version version, boolean allowDowngrade);

    /** Upgrade OS for all nodes of given type to a new version */
    void upgradeOs(ZoneId zone, NodeType type, Version version);

    /** Get target versions for upgrades in given zone */
    TargetVersions targetVersionsOf(ZoneId zone);

    /** Requests firmware checks on all hosts in the given zone. */
    void requestFirmwareCheck(ZoneId zone);

    /** Cancels firmware checks on all hosts in the given zone. */
    void cancelFirmwareCheck(ZoneId zone);

    /** Retire given node */
    void retire(ZoneId zone, String hostname, boolean wantToRetire, boolean wantToDeprovision);

    /** Drop all documents on content nodes in the given zone, application and cluster */
    void dropDocuments(ZoneId zoneId, ApplicationId applicationId, Optional<ClusterSpec.Id> clusterId);

    /** Update reports for given node. A key with null value clears that report */
    void updateReports(ZoneId zone, String hostname, Map<String, String> reports);

    /** Update hardware model */
    void updateModel(ZoneId zone, String hostname, String modelName);

    /** Update switch hostname */
    void updateSwitchHostname(ZoneId zone, String hostname, String switchHostname);

    /** Schedule reboot of given node */
    void reboot(ZoneId zone, String hostname);

    /** Checks whether the zone has the spare capacity to remove the given hosts */
    boolean isReplaceable(ZoneId zone, List<HostName> hostnames);

}
