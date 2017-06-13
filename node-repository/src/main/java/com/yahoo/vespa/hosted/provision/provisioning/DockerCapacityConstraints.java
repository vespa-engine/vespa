package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.cloud.config.ApplicationIdConfig;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.lang.MutableInteger;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enforce allocation constraints for docker by manipulating the NodeList we operate on.
 *
 * The constraints comes in two flavors: headroom and spare.
 *
 * <b>Headroom</b> is the number of docker nodes (of various flavors) we want to reserve for new applications.
 * This is e.g. to make sure we don't smear out small docker flavors on all hosts
 * starving allocations for bigger flavors.
 *
 * <b>Spares</b> is to make sure we have replacement for applications if one or more hosts go down.
 * It is more important to safeguard already onboarded applications than accept new applications.
 *
 * For now - we will use spare also as a means to reserve capacity for future applications that
 * have had a separate AI process.
 *
 * When using spares - we will relay on maintenance jobs to reclaim the spare capacity whenever the
 * capacity has been recovered (e.g. when the dead docker host is replaced)
 *
 * @author smorgrav
 */
public class DockerCapacityConstraints {

    /** This is a static utility class */
    private DockerCapacityConstraints() {}

    /**
     * Spare nodes in first iteration is a node that fills up the two
     * largest hosts (in terms of free capacity)
     */
    public static List<Node> addSpareNodes(List<Node> nodes, int spares) {
        DockerHostCapacity capacity = new DockerHostCapacity(nodes);
        List<Flavor> spareFlavors = nodes.stream()
                .filter(node -> node.type().equals(NodeType.host))
                .filter(dockerHost -> dockerHost.state().equals(Node.State.active))
                .filter(dockerHost -> capacity.freeIPs(dockerHost) > 0)
                .sorted(capacity::compare)
                .limit(spares)
                .map(dockerHost -> freeCapacityAsFlavor(dockerHost, nodes))
                .collect(Collectors.toList());

        return addNodes(nodes, spareFlavors, "spare");
    }

    public static List<Node> addHeadroomAndSpareNodes(List<Node> nodes, NodeFlavors flavors, int nofSpares) {
        List<Node> sparesAndHeadroom = addSpareNodes(nodes, nofSpares);
        return addNodes(sparesAndHeadroom, flavors.getFlavors(), "headroom");
    }

    private static List<Node> addNodes(List<Node> nodes, List<Flavor> flavors, String id) {
        List<Node> headroom = new ArrayList<>(nodes);
        for (Flavor flavor : flavors) {
            int headroomCount = flavor.getIdealHeadroom();
            if (headroomCount > 0) {
                NodeAllocation allocation = createHeadRoomAllocation(flavor, headroomCount, id);
                List<Node> acceptedNodes = DockerAllocator.allocate(allocation, flavor, headroom);
                headroom.addAll(acceptedNodes);
            }
        }
        return headroom;
    }

    private static Flavor freeCapacityAsFlavor(Node host, List<Node> nodes) {
        ResourceCapacity hostCapacity = new ResourceCapacity(host);
        for (Node container : new NodeList(nodes).childNodes(host).asList()) {
            hostCapacity.subtract(container);
        }
        return hostCapacity.asFlavor();
    }

    private static NodeAllocation createHeadRoomAllocation(Flavor flavor, int count, String id) {
        ClusterSpec cluster = ClusterSpec.request(ClusterSpec.Type.container,
                new ClusterSpec.Id(id), new Version());
        ApplicationId appId = new ApplicationId(
                new ApplicationIdConfig(
                        new ApplicationIdConfig.Builder()
                                .tenant(id)
                                .application(id + "-" + flavor.name())
                                .instance("temporarynode")));

        return new NodeAllocation(appId, cluster,  new NodeSpec.CountNodeSpec(count, flavor),
                new MutableInteger(0), Clock.systemUTC());
    }
}