// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostFilter;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.io.IOUtils;
import com.yahoo.restapi.ErrorResponse;
import com.yahoo.restapi.MessageResponse;
import com.yahoo.restapi.Path;
import com.yahoo.restapi.ResourceResponse;
import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.NoSuchNodeException;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.applications.Application;
import com.yahoo.vespa.hosted.provision.autoscale.Load;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsDb;
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.filter.ApplicationFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeHostFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeOsVersionFilter;
import com.yahoo.vespa.hosted.provision.node.filter.NodeTypeFilter;
import com.yahoo.vespa.hosted.provision.node.filter.ParentHostFilter;
import com.yahoo.vespa.hosted.provision.restapi.NodesResponse.ResponseType;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.yolean.Exceptions;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.slime.SlimeUtils.optionalString;

/**
 * The implementation of the /nodes/v2 API.
 * See NodesV2ApiTest for documentation.
 *
 * @author bratseth
 */
public class NodesV2ApiHandler extends ThreadedHttpRequestHandler {

    private final Orchestrator orchestrator;
    private final NodeRepository nodeRepository;
    private final MetricsDb metricsDb;
    private final NodeFlavors nodeFlavors;

    @Inject
    public NodesV2ApiHandler(ThreadedHttpRequestHandler.Context parentCtx, Orchestrator orchestrator,
                             NodeRepository nodeRepository, MetricsDb metricsDb, NodeFlavors flavors) {
        super(parentCtx);
        this.orchestrator = orchestrator;
        this.nodeRepository = nodeRepository;
        this.metricsDb = metricsDb;
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
        catch (ApplicationLockException e) {
            log.log(Level.INFO, "Timed out getting lock when handling '" + request.getUri() + "': " + Exceptions.toMessageString(e));
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
        catch (RuntimeException e) {
            log.log(Level.WARNING, "Unexpected error handling '" + request.getUri() + "'", e);
            return ErrorResponse.internalServerError(Exceptions.toMessageString(e));
        }
    }

    private HttpResponse handleGET(HttpRequest request) {
        Path path = new Path(request.getUri());
        String pathS = request.getUri().getPath();
        if (path.matches(    "/nodes/v2")) return new ResourceResponse(request.getUri(), "node", "state", "acl", "command", "archive", "locks", "maintenance", "upgrade", "capacity", "application", "stats");
        if (path.matches(    "/nodes/v2/node")) return new NodesResponse(ResponseType.nodeList, request, orchestrator, nodeRepository);
        if (pathS.startsWith("/nodes/v2/node/")) return new NodesResponse(ResponseType.singleNode, request, orchestrator, nodeRepository);
        if (path.matches(    "/nodes/v2/state")) return new NodesResponse(ResponseType.stateList, request, orchestrator, nodeRepository);
        if (pathS.startsWith("/nodes/v2/state/")) return new NodesResponse(ResponseType.nodesInStateList, request, orchestrator, nodeRepository);
        if (path.matches(    "/nodes/v2/acl/{hostname}")) return new NodeAclResponse(request, nodeRepository, path.get("hostname"));
        if (path.matches(    "/nodes/v2/command")) return new ResourceResponse(request.getUri(), "restart", "reboot");
        if (path.matches(    "/nodes/v2/archive")) return new ArchiveResponse(nodeRepository);
        if (path.matches(    "/nodes/v2/locks")) return new LocksResponse();
        if (path.matches(    "/nodes/v2/maintenance")) return new JobsResponse(nodeRepository.jobControl());
        if (path.matches(    "/nodes/v2/upgrade")) return new UpgradeResponse(nodeRepository.infrastructureVersions(), nodeRepository.osVersions(), nodeRepository.containerImages());
        if (path.matches(    "/nodes/v2/capacity")) return new HostCapacityResponse(nodeRepository, request);
        if (path.matches(    "/nodes/v2/application")) return applicationList(request.getUri());
        if (path.matches(    "/nodes/v2/application/{applicationId}")) return application(path.get("applicationId"), request.getUri());
        if (path.matches(    "/nodes/v2/stats")) return stats();
        throw new NotFoundException("Nothing at " + path);
    }

    private HttpResponse handlePUT(HttpRequest request) {
        Path path = new Path(request.getUri());
        // Check paths to disallow illegal state changes
        if (path.matches("/nodes/v2/state/ready/{hostname}")) {
            nodeRepository.nodes().markNodeAvailableForNewAllocation(path.get("hostname"), Agent.operator, "Readied through the nodes/v2 API");
            return new MessageResponse("Moved " + path.get("hostname") + " to " + Node.State.ready);
        }
        else if (path.matches("/nodes/v2/state/failed/{hostname}")) {
            var failedOrMarkedNodes = NodeList.copyOf(nodeRepository.nodes().failOrMarkRecursively(path.get("hostname"), Agent.operator, "Failed through the nodes/v2 API"));
            return new MessageResponse("Moved " + hostnamesAsString(failedOrMarkedNodes.state(Node.State.failed).asList()) + " to " + Node.State.failed +
                                       " and marked " + hostnamesAsString(failedOrMarkedNodes.failing().asList()) + " as wantToFail");
        }
        else if (path.matches("/nodes/v2/state/parked/{hostname}")) {
            List<Node> parkedNodes = nodeRepository.nodes().parkRecursively(path.get("hostname"), Agent.operator, "Parked through the nodes/v2 API");
            return new MessageResponse("Moved " + hostnamesAsString(parkedNodes) + " to " + Node.State.parked);
        }
        else if (path.matches("/nodes/v2/state/dirty/{hostname}")) {
            List<Node> dirtiedNodes = nodeRepository.nodes().deallocateRecursively(path.get("hostname"), Agent.operator, "Dirtied through the nodes/v2 API");
            return new MessageResponse("Moved " + hostnamesAsString(dirtiedNodes) + " to " + Node.State.dirty);
        }
        else if (path.matches("/nodes/v2/state/active/{hostname}")) {
            nodeRepository.nodes().reactivate(path.get("hostname"), Agent.operator, "Reactivated through nodes/v2 API");
            return new MessageResponse("Moved " + path.get("hostname") + " to " + Node.State.active);
        }
        else if (path.matches("/nodes/v2/state/breakfixed/{hostname}")) {
            List<Node> breakfixedNodes = nodeRepository.nodes().breakfixRecursively(path.get("hostname"), Agent.operator, "Breakfixed through the nodes/v2 API");
            return new MessageResponse("Moved " + hostnamesAsString(breakfixedNodes) + " to " + Node.State.breakfixed);
        }

        throw new NotFoundException("Cannot put to path '" + path + "'");
    }

    private HttpResponse handlePATCH(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/nodes/v2/node/{hostname}")) {
            NodePatcher patcher = new NodePatcher(nodeFlavors, nodeRepository);
            String hostname = path.get("hostname");
            patcher.patch(hostname, request.getData());
            return new MessageResponse("Updated " + hostname);
        }
        else if (path.matches("/nodes/v2/application/{applicationId}")) {
            try (ApplicationPatcher patcher = new ApplicationPatcher(request.getData(),
                                                                     ApplicationId.fromFullString(path.get("applicationId")),
                                                                     nodeRepository)) {
                nodeRepository.applications().put(patcher.apply(), patcher.lock());
                return new MessageResponse("Updated " + patcher.application());
            }
        }
        else if (path.matches("/nodes/v2/archive/{tenant}")) {
            String uri = requiredField(toSlime(request), "uri", Inspector::asString);
            return setTenantArchiveUri(path.get("tenant"), Optional.of(uri));
        }
        else if (path.matches("/nodes/v2/upgrade/{nodeType}")) {
            return setTargetVersions(path.get("nodeType"), toSlime(request));
        }

        throw new NotFoundException("Nothing at '" + path + "'");
    }

    private HttpResponse handlePOST(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/nodes/v2/command/restart")) {
            int restartCount = nodeRepository.nodes().restartActive(toNodeFilter(request)).size();
            return new MessageResponse("Scheduled restart of " + restartCount + " matching nodes");
        }
        if (path.matches("/nodes/v2/command/reboot")) {
            int rebootCount = nodeRepository.nodes().reboot(toNodeFilter(request)).size();
            return new MessageResponse("Scheduled reboot of " + rebootCount + " matching nodes");
        }
        if (path.matches("/nodes/v2/node")) {
            int addedNodes = addNodes(toSlime(request));
            return new MessageResponse("Added " + addedNodes + " nodes to the provisioned state");
        }
        if (path.matches("/nodes/v2/maintenance/run/{job}")) return runJob(path.get("job"));
        if (path.matches("/nodes/v2/upgrade/firmware")) return requestFirmwareCheckResponse();

        throw new NotFoundException("Nothing at path '" + request.getUri().getPath() + "'");
    }

    private HttpResponse handleDELETE(HttpRequest request) {
        Path path = new Path(request.getUri());
        if (path.matches("/nodes/v2/node/{hostname}")) return deleteNode(path.get("hostname"));
        if (path.matches("/nodes/v2/archive/{tenant}")) return setTenantArchiveUri(path.get("tenant"), Optional.empty());
        if (path.matches("/nodes/v2/upgrade/firmware")) return cancelFirmwareCheckResponse();

        throw new NotFoundException("Nothing at path '" + request.getUri().getPath() + "'");
    }

    private HttpResponse runJob(String job) {
        nodeRepository.jobControl().run(job);
        return new MessageResponse("Executed job '" + job + "'");
    }

    private HttpResponse deleteNode(String hostname) {
        Optional<NodeMutex> nodeMutex = nodeRepository.nodes().lockAndGet(hostname);
        if (nodeMutex.isEmpty()) throw new NotFoundException("No node with hostname '" + hostname + "'");
        try (var lock = nodeMutex.get()) {
            if (lock.node().state() == Node.State.deprovisioned) {
                nodeRepository.nodes().forget(lock.node());
                return new MessageResponse("Permanently removed " + hostname);
            } else {
                List<Node> removedNodes = nodeRepository.nodes().removeRecursively(hostname);
                return new MessageResponse("Removed " + removedNodes.stream().map(Node::hostname).collect(Collectors.joining(", ")));
            }
        }
    }

    public int addNodes(Inspector inspector) {
        List<Node> nodes = createNodesFromSlime(inspector);
        return nodeRepository.nodes().addNodes(nodes, Agent.operator).size();
    }

    private Inspector toSlime(HttpRequest request) {
        try {
            byte[] jsonBytes = IOUtils.readBytes(request.getData(), 1000 * 1000);
            return SlimeUtils.jsonToSlime(jsonBytes).get();
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
        Set<String> ipAddresses = new HashSet<>();
        inspector.field("ipAddresses").traverse((ArrayTraverser) (i, item) -> ipAddresses.add(item.asString()));
        Set<String> ipAddressPool = new HashSet<>();
        inspector.field("additionalIpAddresses").traverse((ArrayTraverser) (i, item) -> ipAddressPool.add(item.asString()));

        List<Address> addressPool = new ArrayList<>();
        inspector.field("additionalHostnames").traverse((ArrayTraverser) (i, item) ->
                addressPool.add(new Address(item.asString())));

        Node.Builder builder = Node.create(inspector.field("id").asString(),
                                           IP.Config.of(ipAddresses, ipAddressPool, addressPool),
                                           inspector.field("hostname").asString(),
                                           flavorFromSlime(inspector),
                                           nodeTypeFromSlime(inspector.field("type")));
        optionalString(inspector.field("parentHostname")).ifPresent(builder::parentHostname);
        optionalString(inspector.field("modelName")).ifPresent(builder::modelName);
        optionalString(inspector.field("reservedTo")).map(TenantName::from).ifPresent(builder::reservedTo);
        optionalString(inspector.field("exclusiveTo")).map(ApplicationId::fromSerializedForm).ifPresent(builder::exclusiveToApplicationId);
        optionalString(inspector.field("switchHostname")).ifPresent(builder::switchHostname);
        return builder.build();
    }

    private Flavor flavorFromSlime(Inspector inspector) {
        Inspector flavorInspector = inspector.field("flavor");
        Inspector resourcesInspector = inspector.field("resources");
        if ( ! flavorInspector.valid()) {
            return new Flavor(new NodeResources(
                    requiredField(resourcesInspector, "vcpu", Inspector::asDouble),
                    requiredField(resourcesInspector, "memoryGb", Inspector::asDouble),
                    requiredField(resourcesInspector, "diskGb", Inspector::asDouble),
                    requiredField(resourcesInspector, "bandwidthGbps", Inspector::asDouble),
                    optionalString(resourcesInspector.field("diskSpeed")).map(NodeResourcesSerializer::diskSpeedFrom).orElse(NodeResources.DiskSpeed.getDefault()),
                    optionalString(resourcesInspector.field("storageType")).map(NodeResourcesSerializer::storageTypeFrom).orElse(NodeResources.StorageType.getDefault()),
                    optionalString(resourcesInspector.field("architecture")).map(NodeResourcesSerializer::architectureFrom).orElse(NodeResources.Architecture.getDefault())));
        }

        Flavor flavor = nodeFlavors.getFlavorOrThrow(flavorInspector.asString());
        if (resourcesInspector.valid()) {
            if (resourcesInspector.field("vcpu").valid())
                flavor = flavor.with(flavor.resources().withVcpu(resourcesInspector.field("vcpu").asDouble()));
            if (resourcesInspector.field("memoryGb").valid())
                flavor = flavor.with(flavor.resources().withMemoryGb(resourcesInspector.field("memoryGb").asDouble()));
            if (resourcesInspector.field("diskGb").valid())
                flavor = flavor.with(flavor.resources().withDiskGb(resourcesInspector.field("diskGb").asDouble()));
            if (resourcesInspector.field("bandwidthGbps").valid())
                flavor = flavor.with(flavor.resources().withBandwidthGbps(resourcesInspector.field("bandwidthGbps").asDouble()));
            if (resourcesInspector.field("diskSpeed").valid())
                flavor = flavor.with(flavor.resources().with(NodeResourcesSerializer.diskSpeedFrom(resourcesInspector.field("diskSpeed").asString())));
            if (resourcesInspector.field("storageType").valid())
                flavor = flavor.with(flavor.resources().with(NodeResourcesSerializer.storageTypeFrom(resourcesInspector.field("storageType").asString())));
            if (resourcesInspector.field("architecture").valid())
                flavor = flavor.with(flavor.resources().with(NodeResourcesSerializer.architectureFrom(resourcesInspector.field("architecture").asString())));
        }
        return flavor;
    }

    private static <T> T requiredField(Inspector inspector, String fieldName, Function<Inspector, T> valueExtractor) {
        Inspector field = inspector.field(fieldName);
        if (!field.valid()) throw new IllegalArgumentException("Required field '" + fieldName + "' is missing");
        return valueExtractor.apply(field);
    }

    private NodeType nodeTypeFromSlime(Inspector object) {
        if (! object.valid()) return NodeType.tenant; // default
        return NodeSerializer.typeFrom(object.asString());
    }

    public static NodeFilter toNodeFilter(HttpRequest request) {
        return NodeFilter.in(request.getProperty("state"),
                             request.getBooleanProperty("includeDeprovisioned"))
                         .matching(NodeHostFilter.from(HostFilter.from(request.getProperty("hostname"),
                                                                       request.getProperty("flavor"),
                                                                       request.getProperty("clusterType"),
                                                                       request.getProperty("clusterId")))
                                                 .and(ApplicationFilter.from(request.getProperty("application")))
                                                 .and(NodeTypeFilter.from(request.getProperty("type")))
                                                 .and(ParentHostFilter.from(request.getProperty("parentHost")))
                                                 .and(NodeOsVersionFilter.from(request.getProperty("osVersion"))));
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

    private MessageResponse setTargetVersions(String nodeTypeS, Inspector inspector) {
        NodeType nodeType = NodeType.valueOf(nodeTypeS.toLowerCase());
        List<String> messageParts = new ArrayList<>();

        boolean force = inspector.field("force").asBool();
        Inspector versionField = inspector.field("version");
        Inspector osVersionField = inspector.field("osVersion");

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

        if (messageParts.isEmpty()) {
            throw new IllegalArgumentException("At least one of 'version' or 'osVersion' must be set");
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

    private HttpResponse setTenantArchiveUri(String tenant, Optional<String> archiveUri) {
        nodeRepository.archiveUris().setArchiveUri(TenantName.from(tenant), archiveUri);
        return new MessageResponse(archiveUri.map(a -> "Updated").orElse("Removed") + " archive URI for " + tenant);
    }

    private static String hostnamesAsString(List<Node> nodes) {
        if (nodes.isEmpty()) return "none";
        return nodes.stream().map(Node::hostname).sorted().collect(Collectors.joining(", "));
    }

    private HttpResponse applicationList(URI uri) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor applications = root.setArray("applications");
        for (ApplicationId id : nodeRepository.applications().ids()) {
            Cursor application = applications.addObject();
            application.setString("url", withPath("/nodes/v2/application/" + id.toFullString(), uri).toString());
            application.setString("id", id.toFullString());
        }
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse application(String idString, URI uri) {
        ApplicationId id = ApplicationId.fromFullString(idString);
        Optional<Application> application = nodeRepository.applications().get(id);
        if (application.isEmpty())
            return ErrorResponse.notFoundError("No application '" + id + "'");
        Slime slime = ApplicationSerializer.toSlime(application.get(),
                                                    nodeRepository.nodes().list(Node.State.active).owner(id),
                                                    metricsDb,
                                                    nodeRepository,
                                                    withPath("/nodes/v2/applications/" + id, uri));
        return new SlimeJsonResponse(slime);
    }

    private HttpResponse stats() {
        var stats = nodeRepository.computeStats();

        Slime slime = new Slime();
        Cursor root = slime.setObject();

        root.setDouble("totalCost", stats.totalCost());
        root.setDouble("totalAllocatedCost", stats.totalAllocatedCost());
        toSlime(stats.load(), root.setObject("load"));
        toSlime(stats.activeLoad(), root.setObject("activeLoad"));
        Cursor applicationsArray = root.setArray("applications");
        for (int i = 0; i <= 5; i++) {
            if (i >= stats.applicationStats().size()) break;

            var applicationStats = stats.applicationStats().get(i);
            Cursor applicationObject = applicationsArray.addObject();
            applicationObject.setString("id", applicationStats.id().toFullString());
            toSlime(applicationStats.load(), applicationObject.setObject("load"));
            applicationObject.setDouble("cost", applicationStats.cost());
            applicationObject.setDouble("unutilizedCost", applicationStats.unutilizedCost());
        }
        return new SlimeJsonResponse(slime);
    }

    private void toSlime(Load load, Cursor object) {
        object.setDouble("cpu", load.cpu());
        object.setDouble("memory", load.memory());
        object.setDouble("disk", load.disk());
    }

    /** Returns a copy of the given URI with the host and port from the given URI and the path set to the given path */
    private URI withPath(String newPath, URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getUserInfo(), uri.getHost(), uri.getPort(), newPath, null, null);
        }
        catch (URISyntaxException e) {
            throw new RuntimeException("Will not happen", e);
        }
    }

}
