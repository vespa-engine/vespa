// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.serialization.NetworkPortsSerializer;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.vespa.applicationmodel.HostName;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.flags.StringFlag;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.TrustStoreItem;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.orchestrator.status.HostInfo;
import com.yahoo.vespa.orchestrator.status.HostStatus;

import java.net.URI;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
* @author bratseth
*/
class NodesResponse extends SlimeJsonResponse {

    /** The responses this can create */
    public enum ResponseType { nodeList, stateList, nodesInStateList, singleNode }

    /** The request url minus parameters, with a trailing slash added if missing */
    private final String parentUrl;

    /** The parent url of nodes */
    private final String nodeParentUrl;

    private final NodeFilter filter;
    private final boolean recursive;
    private final Function<HostName, Optional<HostInfo>> orchestrator;
    private final NodeRepository nodeRepository;
    private final StringFlag wantedDockerTagFlag;

    public NodesResponse(ResponseType responseType, HttpRequest request,
                         Orchestrator orchestrator, NodeRepository nodeRepository) {
        this.parentUrl = toParentUrl(request);
        this.nodeParentUrl = toNodeParentUrl(request);
        this.recursive = request.getBooleanProperty("recursive");
        this.orchestrator = orchestrator.getHostResolver();
        this.nodeRepository = nodeRepository;
        this.wantedDockerTagFlag = PermanentFlags.WANTED_DOCKER_TAG.bindTo(nodeRepository.flagSource());

        // Cannot use Set.of() because the nodeRepository account can also be the empty account (at least in tests).
        var nonEnclaveAccounts = new HashSet<>(Arrays.asList(CloudAccount.empty, nodeRepository.zone().cloud().account()));
        this.filter = NodesV2ApiHandler.toNodeFilter(request, nonEnclaveAccounts);

        Cursor root = slime.setObject();
        switch (responseType) {
            case nodeList -> nodesToSlime(filter.states(), root);
            case stateList -> statesToSlime(root);
            case nodesInStateList -> nodesToSlime(Set.of(NodeSerializer.stateFrom(lastElement(parentUrl))), root);
            case singleNode -> nodeToSlime(lastElement(parentUrl), root);
        }
    }

    private String toParentUrl(HttpRequest request) {
        URI uri = request.getUri();
        String parentUrl = uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + uri.getPath();
        if ( ! parentUrl.endsWith("/"))
            parentUrl = parentUrl + "/";
        return parentUrl;
    }

    private String toNodeParentUrl(HttpRequest request) {
        URI uri = request.getUri();
        return uri.getScheme() + "://" + uri.getHost() + ":" + uri.getPort() + "/nodes/v2/node/";
    }

    private void statesToSlime(Cursor root) {
        Cursor states = root.setObject("states");
        for (Node.State state : Node.State.values())
            toSlime(state, states.setObject(NodeSerializer.toString(state)));
    }

    private void toSlime(Node.State state, Cursor object) {
        object.setString("url", parentUrl + NodeSerializer.toString(state));
        if (recursive)
            nodesToSlime(Set.of(state), object);
    }

    /** Outputs the nodes in the given states to a node array */
    private void nodesToSlime(Set<Node.State> states, Cursor parentObject) {
        Cursor nodeArray = parentObject.setArray("nodes");
        nodeRepository.nodes().list()
                      .state(states)
                      .matching(filter)
                      .sortedBy(Comparator.comparing(Node::hostname))
                      .forEach(node -> toSlime(node, recursive, nodeArray.addObject()));
    }

    private void nodeToSlime(String hostname, Cursor object) {
        Node node = nodeRepository.nodes().node(hostname).orElseThrow(() ->
                new NotFoundException("No node with hostname '" + hostname + "'"));
        toSlime(node, true, object);
    }

    private void toSlime(Node node, boolean allFields, Cursor object) {
        NodeResources realResources = nodeRepository.resourcesCalculator().realResourcesOf(node, nodeRepository);

        object.setString("url", nodeParentUrl + node.hostname());
        if ( ! allFields) return;
        object.setString("id", node.id());
        object.setString("state", NodeSerializer.toString(node.state()));
        object.setString("type", NodeSerializer.toString(node.type()));
        object.setString("hostname", node.hostname());
        if (node.parentHostname().isPresent()) {
            object.setString("parentHostname", node.parentHostname().get());
        }
        object.setString("flavor", node.flavor().name());
        node.reservedTo().ifPresent(reservedTo -> object.setString("reservedTo", reservedTo.value()));
        node.exclusiveToApplicationId().ifPresent(applicationId -> object.setString("exclusiveTo", applicationId.serializedForm()));
        node.exclusiveToClusterType().ifPresent(clusterType -> object.setString("exclusiveToClusterType", clusterType.name()));
        if (node.flavor().isConfigured())
            object.setDouble("cpuCores", node.flavor().resources().vcpu());
        NodeResourcesSerializer.toSlime(node.flavor().resources(), object.setObject("resources"));
        NodeResourcesSerializer.toSlime(realResources, object.setObject("realResources"));
        if (node.flavor().cost() > 0)
            object.setLong("cost", node.flavor().cost());
        object.setString("environment", node.flavor().getType().name());
        node.allocation().ifPresent(allocation -> {
            toSlime(allocation.owner(), object.setObject("owner"));
            toSlime(allocation.membership(), object.setObject("membership"));
            object.setLong("restartGeneration", allocation.restartGeneration().wanted());
            object.setLong("currentRestartGeneration", allocation.restartGeneration().current());
            object.setString("wantedDockerImage", nodeRepository.containerImages().get(node).withTag(resolveVersionFlag(wantedDockerTagFlag, node, allocation)).asString());
            object.setString("wantedVespaVersion", allocation.membership().cluster().vespaVersion().toFullString());
            NodeResourcesSerializer.toSlime(allocation.requestedResources(), object.setObject("requestedResources"));
            allocation.networkPorts().ifPresent(ports -> NetworkPortsSerializer.toSlime(ports, object.setArray("networkPorts")));
            orchestrator.apply(new HostName(node.hostname()))
                        .ifPresent(info -> {
                            if (info.status() != HostStatus.NO_REMARKS) {
                                object.setString("orchestratorStatus", info.status().asString());
                            }
                            info.suspendedSince().ifPresent(since -> object.setLong("suspendedSinceMillis", since.toEpochMilli()));
                        });
        });
        object.setLong("rebootGeneration", node.status().reboot().wanted());
        object.setLong("currentRebootGeneration", node.status().reboot().current());
        node.status().osVersion().current().ifPresent(version -> object.setString("currentOsVersion", version.toFullString()));
        node.status().osVersion().wanted().ifPresent(version -> object.setString("wantedOsVersion", version.toFullString()));
        if (node.type().isHost()) {
            object.setBool("deferOsUpgrade", !nodeRepository.osVersions().canUpgrade(node));
        }
        node.status().firmwareVerifiedAt().ifPresent(instant -> object.setLong("currentFirmwareCheck", instant.toEpochMilli()));
        if (node.type().isHost())
            nodeRepository.firmwareChecks().requiredAfter().ifPresent(after -> object.setLong("wantedFirmwareCheck", after.toEpochMilli()));
        node.status().vespaVersion().ifPresent(version -> object.setString("vespaVersion", version.toFullString()));
        currentContainerImage(node).ifPresent(image -> object.setString("currentDockerImage", image.asString()));
        object.setLong("failCount", node.status().failCount());
        object.setBool("wantToRetire", node.status().wantToRetire());
        object.setBool("preferToRetire", node.status().preferToRetire());
        object.setBool("wantToDeprovision", node.status().wantToDeprovision());
        object.setBool("wantToRebuild", node.status().wantToRebuild());
        object.setBool("down", node.isDown());
        toSlime(node.history().events(), object.setArray("history"));
        toSlime(node.history().log(), object.setArray("log"));
        ipAddressesToSlime(node.ipConfig().primary(), object.setArray("ipAddresses"));
        ipAddressesToSlime(node.ipConfig().pool().ipSet(), object.setArray("additionalIpAddresses"));
        addressesToSlime(node.ipConfig().pool().getAddressList(), object);
        node.reports().toSlime(object, "reports");
        node.modelName().ifPresent(modelName -> object.setString("modelName", modelName));
        node.switchHostname().ifPresent(switchHostname -> object.setString("switchHostname", switchHostname));
        nodeRepository.archiveUriManager().archiveUriFor(node).ifPresent(uri -> object.setString("archiveUri", uri));
        trustedCertsToSlime(node.trustedCertificates(), object);
        if (!node.cloudAccount().isUnspecified()) {
            object.setString("cloudAccount", node.cloudAccount().value());
        }
        node.wireguardPubKey().ifPresent(key -> object.setString("wireguardPubkey", key.value()));
    }

    private Version resolveVersionFlag(StringFlag flag, Node node, Allocation allocation) {
        String value = flag
                .with(FetchVector.Dimension.HOSTNAME, node.hostname())
                .with(FetchVector.Dimension.NODE_TYPE, node.type().name())
                .with(FetchVector.Dimension.TENANT_ID, allocation.owner().tenant().value())
                .with(FetchVector.Dimension.APPLICATION_ID, allocation.owner().serializedForm())
                .with(FetchVector.Dimension.CLUSTER_TYPE, allocation.membership().cluster().type().name())
                .with(FetchVector.Dimension.CLUSTER_ID, allocation.membership().cluster().id().value())
                .with(FetchVector.Dimension.VESPA_VERSION, allocation.membership().cluster().vespaVersion().toFullString())
                .value();

        return value.isEmpty() ?
               allocation.membership().cluster().vespaVersion() :
               value.indexOf('.') == -1 ?
               allocation.membership().cluster().vespaVersion().withQualifier(value) :
               new Version(value);
    }

    private void toSlime(ApplicationId id, Cursor object) {
        object.setString("tenant", id.tenant().value());
        object.setString("application", id.application().value());
        object.setString("instance", id.instance().value());
    }

    private void toSlime(ClusterMembership membership, Cursor object) {
        object.setString("clustertype", membership.cluster().type().name());
        object.setString("clusterid", membership.cluster().id().value());
        object.setString("group", String.valueOf(membership.cluster().group().get().index()));
        object.setLong("index", membership.index());
        object.setBool("retired", membership.retired());
    }

    private void toSlime(Collection<History.Event> events, Cursor array) {
        for (History.Event event : events) {
            Cursor object = array.addObject();
            object.setString("event", event.type().name());
            object.setLong("at", event.at().toEpochMilli());
            object.setString("agent", event.agent().name());
        }
    }

    private Optional<DockerImage> currentContainerImage(Node node) {
        if (node.status().containerImage().isPresent()) {
            return node.status().containerImage();
        }
        if (node.type().isHost()) {
            // Return the image used by children of this host. This is used by host-admin to preload container images.
            return node.status().vespaVersion().map(version -> nodeRepository.containerImages().get(node).withTag(version));
        }
        return Optional.empty();
    }

    static void ipAddressesToSlime(Set<String> ipAddresses, Cursor array) {
        ipAddresses.forEach(array::addString);
    }

    private void addressesToSlime(List<Address> addresses, Cursor object) {
        if (addresses.isEmpty()) return;
        // When/if Address becomes richer: add another field (e.g. "addresses") and expand to array of objects
        Cursor addressesArray = object.setArray("additionalHostnames");
        addresses.forEach(address -> addressesArray.addString(address.hostname()));
    }

    private void trustedCertsToSlime(List<TrustStoreItem> trustStoreItems, Cursor object) {
        if (trustStoreItems.isEmpty()) return;
        Cursor array = object.setArray("trustStore");
        trustStoreItems.forEach(cert -> cert.toSlime(array));
    }

    private String lastElement(String path) {
        if (path.endsWith("/"))
            path = path.substring(0, path.length()-1);
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash < 0) return path;
        return path.substring(lastSlash+1);
    }

}
