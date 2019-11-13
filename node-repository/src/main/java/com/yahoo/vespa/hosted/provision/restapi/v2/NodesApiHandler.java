// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.component.Version;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.NoSuchNodeException;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.filter.ApplicationFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeHostFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeOsVersionFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeTypeFilter;
import com.yahoo.vespa.hosted.provision.node.filter.ParentHostFilter;
import com.yahoo.vespa.hosted.provision.node.filter.StateFilter;
import com.yahoo.vespa.hosted.provision.restapi.v2.NodesResponse.ResponseType;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.yolean.Exceptions;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static com.yahoo.vespa.config.SlimeUtils.optionalString;

/**
 * The implementation of the /nodes/v2 API.
 * See RestApiTest for documentation.
 *
 * @author bratseth
 */
public class NodesApiHandler extends LoggingRequestHandler {

    private final Orchestrator orchestrator;
    private final NodeRepository nodeRepository;
    private final NodeFlavors nodeFlavors;
    private final NodeSerializer serializer = new NodeSerializer();

    @Inject
    public NodesApiHandler(LoggingRequestHandler.Context parentCtx, Orchestrator orchestrator,
                           NodeRepository nodeRepository, NodeFlavors flavors) {
        super(parentCtx);
        this.orchestrator = orchestrator;
        this.nodeRepository = nodeRepository;
        this.nodeFlavors = flavors;
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            switch (request.getMethod()) {
                case GET: return handleGET(request);
                case PUT: return handlePUT(request);
                case POST: return isPatchOverride(request) ? handlePATCH(request) : handlePOST(request);
                case DELETE: return handleDELETE(request);
                case PATCH: return handlePATCH(request);
                default: return ErrorResponse.methodNotAllowed("Method '" + request.getMethod() + "' is not supported");
            }
        }
        catch (NotFoundException | NoSuchNodeException e) {
            return ErrorResponse.notFoundError(Exceptions.toMessageString(e));
        }
        catch (IllegalArgumentException e) {
            return ErrorResponse.badRequest(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        String path = request.getUri().getPath();
        if (path.equals(    "/nodes/v2/")) return new ResourceResponse(request.getUri(), "state", "node", "command", "maintenance", "upgrade");
        if (path.equals(    "/nodes/v2/node/")) return new NodesResponse(ResponseType.nodeList, request, orchestrator, nodeRepository);
        if (path.startsWith("/nodes/v2/node/")) return new NodesResponse(ResponseType.singleNode, request, orchestrator, nodeRepository);
        if (path.equals(    "/nodes/v2/state/")) return new NodesResponse(ResponseType.stateList, request, orchestrator, nodeRepository);
        if (path.startsWith("/nodes/v2/state/")) return new NodesResponse(ResponseType.nodesInStateList, request, orchestrator, nodeRepository);
        if (path.startsWith("/nodes/v2/acl/")) return new NodeAclResponse(request, nodeRepository);
        if (path.equals(    "/nodes/v2/command/")) return new ResourceResponse(request.getUri(), "restart", "reboot");
        if (path.equals(    "/nodes/v2/maintenance/")) return new JobsResponse(nodeRepository.jobControl());
        if (path.equals(    "/nodes/v2/upgrade/")) return new UpgradeResponse(nodeRepository.infrastructureVersions(), nodeRepository.osVersions(), nodeRepository.dockerImages());
        if (path.startsWith("/nodes/v2/capacity")) return new HostCapacityResponse(nodeRepository, request);
        throw new NotFoundException("Nothing at path '" + path + "'");
    }

    private HttpResponse handlePUT(HttpRequest request) {
        String path = request.getUri().getPath();
        // Check paths to disallow illegal state changes
        if (path.startsWith("/nodes/v2/state/ready/") ||
            path.startsWith("/nodes/v2/state/availablefornewallocations/")) {
            nodeRepository.markNodeAvailableForNewAllocation(lastElement(path), Agent.operator, "Readied through the nodes/v2 API");
            return new MessageResponse("Moved " + lastElement(path) + " to ready");
        }
        else if (path.startsWith("/nodes/v2/state/failed/")) {
            List<Node> failedNodes = nodeRepository.failRecursively(lastElement(path), Agent.operator, "Failed through the nodes/v2 API");
            return new MessageResponse("Moved " + hostnamesAsString(failedNodes) + " to failed");
        }
        else if (path.startsWith("/nodes/v2/state/parked/")) {
            List<Node> parkedNodes = nodeRepository.parkRecursively(lastElement(path), Agent.operator, "Parked through the nodes/v2 API");
            return new MessageResponse("Moved " + hostnamesAsString(parkedNodes) + " to parked");
        }
        else if (path.startsWith("/nodes/v2/state/dirty/")) {
            List<Node> dirtiedNodes = nodeRepository.dirtyRecursively(lastElement(path), Agent.operator, "Dirtied through the nodes/v2 API");
            return new MessageResponse("Moved " + hostnamesAsString(dirtiedNodes) + " to dirty");
        }
        else if (path.startsWith("/nodes/v2/state/active/")) {
            nodeRepository.reactivate(lastElement(path), Agent.operator, "Reactivated through nodes/v2 API");
            return new MessageResponse("Moved " + lastElement(path) + " to active");
        }

        throw new NotFoundException("Cannot put to path '" + path + "'");
    }

    private HttpResponse handlePATCH(HttpRequest request) {
        String path = request.getUri().getPath();
        if (path.startsWith("/nodes/v2/node/")) {
            Node node = nodeFromRequest(request);
            try (var lock = nodeRepository.lock(node)) {
                var patchedNode = new NodePatcher(nodeFlavors, request.getData(), node, nodeRepository.list(lock),
                                                  nodeRepository.clock()).apply();
                nodeRepository.write(patchedNode, lock);
            }
            return new MessageResponse("Updated " + node.hostname());
        }
        else if (path.startsWith("/nodes/v2/upgrade/")) {
            return setTargetVersions(request);
        }

        throw new NotFoundException("Nothing at '" + path + "'");
    }

    private HttpResponse handlePOST(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/nodes/v2/command/restart")) {
            int restartCount = nodeRepository.restart(toNodeFilter(request)).size();
            return new MessageResponse("Scheduled restart of " + restartCount + " matching nodes");
        }
        if (path.matches("/nodes/v2/command/reboot")) {
            int rebootCount = nodeRepository.reboot(toNodeFilter(request)).size();
            return new MessageResponse("Scheduled reboot of " + rebootCount + " matching nodes");
        }
        if (path.matches("/nodes/v2/node")) {
            int addedNodes = addNodes(request.getData());
            return new MessageResponse("Added " + addedNodes + " nodes to the provisioned state");
        }
        if (path.matches("/nodes/v2/maintenance/inactive/{job}")) return setJobActive(path.get("job"), false);
        if (path.matches("/nodes/v2/upgrade/firmware")) return requestFirmwareCheckResponse();

        throw new NotFoundException("Nothing at path '" + request.getUri().getPath() + "'");
    }

    private HttpResponse handleDELETE(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/nodes/v2/node/{hostname}")) {
            String hostname = path.get("hostname");
            List<Node> removedNodes = nodeRepository.removeRecursively(hostname);
            return new MessageResponse("Removed " + removedNodes.stream().map(Node::hostname).collect(Collectors.joining(", ")));
        }
        if (path.matches("/nodes/v2/maintenance/inactive/{job}")) return setJobActive(path.get("job"), true);
        if (path.matches("/nodes/v2/upgrade/firmware")) return cancelFirmwareCheckResponse();

        throw new NotFoundException("Nothing at path '" + request.getUri().getPath() + "'");
    }

    private Node nodeFromRequest(HttpRequest request) {
        String hostname = lastElement(request.getUri().getPath());
        return nodeRepository.getNode(hostname).orElseThrow(() ->
                new NotFoundException("No node found with hostname " + hostname));
    }

    public int addNodes(InputStream jsonStream) {
        List<Node> nodes = createNodesFromSlime(toSlime(jsonStream).get());
        return nodeRepository.addNodes(nodes).size();
    }

    private Slime toSlime(InputStream jsonStream) {
        try {
            byte[] jsonBytes = IOUtils.readBytes(jsonStream, 1000 * 1000);
            return SlimeUtils.jsonToSlime(jsonBytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<Node> createNodesFromSlime(Inspector object) {
        List<Node> nodes = new ArrayList<>();
        object.traverse((ArrayTraverser) (int i, Inspector item) -> nodes.add(createNode(item)));
        return nodes;
    }

    private Node createNode(Inspector inspector) {
        Optional<String> parentHostname = optionalString(inspector.field("parentHostname"));
        Optional<String> modelName = optionalString(inspector.field("modelName"));
        Set<String> ipAddresses = new HashSet<>();
        inspector.field("ipAddresses").traverse((ArrayTraverser) (i, item) -> ipAddresses.add(item.asString()));
        Set<String> ipAddressPool = new HashSet<>();
        inspector.field("additionalIpAddresses").traverse((ArrayTraverser) (i, item) -> ipAddressPool.add(item.asString()));

        return Node.create(
                inspector.field("openStackId").asString(),
                new IP.Config(ipAddresses, ipAddressPool),
                inspector.field("hostname").asString(),
                parentHostname,
                modelName,
                flavorFromSlime(inspector),
                nodeTypeFromSlime(inspector.field("type")));
    }

    private Flavor flavorFromSlime(Inspector inspector) {
        Inspector flavorInspector = inspector.field("flavor");
        if ( ! flavorInspector.valid()) {
            return new Flavor(new NodeResources(
                    requiredField(inspector, "minCpuCores", Inspector::asDouble),
                    requiredField(inspector, "minMainMemoryAvailableGb", Inspector::asDouble),
                    requiredField(inspector, "minDiskAvailableGb", Inspector::asDouble),
                    requiredField(inspector, "bandwidthGbps", Inspector::asDouble),
                    requiredField(inspector, "fastDisk", Inspector::asBool) ? fast : slow,
                    requiredField(inspector, "remoteStorage", Inspector::asBool) ? remote : local));
        }

        Flavor flavor = nodeFlavors.getFlavorOrThrow(flavorInspector.asString());
        if (inspector.field("minCpuCores").valid())
            flavor = flavor.with(flavor.resources().withVcpu(inspector.field("minCpuCores").asDouble()));
        if (inspector.field("minMainMemoryAvailableGb").valid())
            flavor = flavor.with(flavor.resources().withMemoryGb(inspector.field("minMainMemoryAvailableGb").asDouble()));
        if (inspector.field("minDiskAvailableGb").valid())
            flavor = flavor.with(flavor.resources().withDiskGb(inspector.field("minDiskAvailableGb").asDouble()));
        if (inspector.field("bandwidthGbps").valid())
            flavor = flavor.with(flavor.resources().withBandwidthGbps(inspector.field("bandwidthGbps").asDouble()));
        if (inspector.field("fastDisk").valid())
            flavor = flavor.with(flavor.resources().with(inspector.field("fastDisk").asBool() ? fast : slow));
        if (inspector.field("remoteStorage").valid())
            flavor = flavor.with(flavor.resources().with(inspector.field("remoteStorage").asBool() ? remote : local));
        return flavor;
    }

    private static <T> T requiredField(Inspector inspector, String fieldName, Function<Inspector, T> valueExtractor) {
        Inspector field = inspector.field(fieldName);
        if (!field.valid()) throw new IllegalArgumentException("Required field '" + fieldName + "' is missing");
        return valueExtractor.apply(field);
    }

    private NodeType nodeTypeFromSlime(Inspector object) {
        if (! object.valid()) return NodeType.tenant; // default
        return serializer.typeFrom(object.asString());
    }

    public static NodeFilter toNodeFilter(HttpRequest request) {
        NodeFilter filter = NodeHostFilter.from(HostFilter.from(request.getProperty("hostname"),
                                                                request.getProperty("flavor"),
                                                                request.getProperty("clusterType"),
                                                                request.getProperty("clusterId")));
        filter = ApplicationFilter.from(request.getProperty("application"), filter);
        filter = StateFilter.from(request.getProperty("state"), filter);
        filter = NodeTypeFilter.from(request.getProperty("type"), filter);
        filter = ParentHostFilter.from(request.getProperty("parentHost"), filter);
        filter = NodeOsVersionFilter.from(request.getProperty("osVersion"), filter);
        return filter;
    }

    private static String lastElement(String path) {
        if (path.endsWith("/"))
            path = path.substring(0, path.length()-1);
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash < 0) return path;
        return path.substring(lastSlash + 1);
    }

    private static boolean isPatchOverride(HttpRequest request) {
        // Since Jersey's HttpUrlConnector does not support PATCH we support this by override this on POST requests.
        String override = request.getHeader("X-HTTP-Method-Override");
        if (override != null) {
            if (override.equals("PATCH")) {
                return true;
            } else {
                String msg = String.format("Illegal X-HTTP-Method-Override header for POST request. Accepts 'PATCH' but got '%s'", override);
                throw new IllegalArgumentException(msg);
            }
        }
        return false;
    }

    private MessageResponse setJobActive(String jobName, boolean active) {
        if ( ! nodeRepository.jobControl().jobs().contains(jobName))
            throw new NotFoundException("No job named '" + jobName + "'");
        nodeRepository.jobControl().setActive(jobName, active);
        return new MessageResponse((active ? "Re-activated" : "Deactivated" ) + " job '" + jobName + "'");
    }

    private MessageResponse setTargetVersions(HttpRequest request) {
        NodeType nodeType = NodeType.valueOf(lastElement(request.getUri().getPath()).toLowerCase());
        Inspector inspector = toSlime(request.getData()).get();
        List<String> messageParts = new ArrayList<>(3);

        boolean force = inspector.field("force").asBool();
        Inspector versionField = inspector.field("version");
        Inspector osVersionField = inspector.field("osVersion");
        Inspector dockerImageField = inspector.field("dockerImage");

        if (versionField.valid()) {
            Version version = Version.fromString(versionField.asString());
            nodeRepository.infrastructureVersions().setTargetVersion(nodeType, version, force);
            messageParts.add("version to " + version.toFullString());
        }

        if (osVersionField.valid()) {
            String v = osVersionField.asString();
            if (v.isEmpty()) {
                nodeRepository.osVersions().removeTarget(nodeType);
                messageParts.add("osVersion to null");
            } else {
                Version osVersion = Version.fromString(v);
                nodeRepository.osVersions().setTarget(nodeType, osVersion, force);
                messageParts.add("osVersion to " + osVersion.toFullString());
            }
        }

        if (dockerImageField.valid()) {
            Optional<DockerImage> dockerImage = Optional.of(dockerImageField.asString())
                    .filter(s -> !s.isEmpty())
                    .map(DockerImage::fromString);
            nodeRepository.dockerImages().setDockerImage(nodeType, dockerImage);
            messageParts.add("docker image to " + dockerImage.map(DockerImage::asString).orElse(null));
        }

        if (messageParts.isEmpty()) {
            throw new IllegalArgumentException("At least one of 'version', 'osVersion' or 'dockerImage' must be set");
        }

        return new MessageResponse("Set " + String.join(", ", messageParts) +
                                   " for nodes of type " + nodeType);
    }

    private MessageResponse cancelFirmwareCheckResponse() {
        nodeRepository.firmwareChecks().cancel();
        return new MessageResponse("Cancelled outstanding requests for firmware checks");
    }

    private MessageResponse requestFirmwareCheckResponse() {
        nodeRepository.firmwareChecks().request();
        return new MessageResponse("Will request firmware checks on all hosts.");
    }

    private static String hostnamesAsString(List<Node> nodes) {
        return nodes.stream().map(Node::hostname).sorted().collect(Collectors.joining(", "));
    }

}
