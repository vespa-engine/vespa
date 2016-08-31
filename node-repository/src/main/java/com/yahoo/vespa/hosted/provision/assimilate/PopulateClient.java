// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.assimilate;

import com.google.common.collect.ImmutableMap;
import com.yahoo.config.provision.*;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Configuration;
import com.yahoo.vespa.hosted.provision.node.Flavor;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.NodeFlavors;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.persistence.NodeSerializer;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPathConstants;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
* @author vegard
*/
// TODO Moved here from hosted repo as is, more work to be done before it is usable
public class PopulateClient {

    static final Map<String, String> CLUSTER_TYPE_ELEMENT = ImmutableMap.of("container", "jdisc", "content", "content");
    static final String CONTAINER_CLUSTER_TYPE = "container";
    static final String CONTENT_CLUSTER_TYPE = "content";

    private final String tenantId;
    private final String applicationId;
    private final String instanceId;
    private final Document servicesXml;
    private final Map<String, String> flavorSpec;
    private final Map<String, String> hostMapping;
    private final boolean dryRun;

    private final NodeFlavors nodeFlavors;

    private Clock creationClock = Clock.systemUTC();
    private CuratorDatabaseClient zkClient;

    // TODO NodeFlavors is now based on configuration, so callers need to do some work to get a proper NodeFlavors injected
    public PopulateClient(Curator curator, NodeFlavors nodeFlavors, String tenantId, String applicationId, String instanceId,
                          String servicesXmlFilename, String hostsXmlFilename, Map<String, String> flavorSpec, boolean dryRun) {
        this.nodeFlavors = nodeFlavors;
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.instanceId = instanceId;
        this.servicesXml = XmlUtils.parseXml(servicesXmlFilename);
        this.hostMapping = XmlUtils.getHostMapping(XmlUtils.parseXml(hostsXmlFilename));
        this.flavorSpec = flavorSpec;
        this.dryRun = dryRun;
        this.zkClient = new CuratorDatabaseClient(nodeFlavors, curator, creationClock);
        
        ensureFlavorIsDefinedForEveryCluster();
    }

    public void populate(String clusterType) {
        final List<Node> nodes = getNodesForCluster(clusterType);

        if (dryRun) {
            System.out.println("Will populate zookeeper with the following:");
            nodes.stream().forEach(node -> System.out.println(byteArrayToUTF8(new NodeSerializer(nodeFlavors).toJson(node))));
            return;
        }

        zkClient.addNodesInState(nodes, Node.State.active);
    }

    private Optional<Flavor> getFlavor(String clusterType, String clusterId) {
      return nodeFlavors.getFlavor(flavorSpec.get(clusterType + "." + clusterId));
    }

    private boolean hasDefinedFlavor(String clusterType, String clusterId) {
        return flavorSpec.containsKey(clusterType + "." + clusterId);
    }

    private Node buildNode(String hostname, String clusterType, String clusterId, int nodeIndex) {
        int group = 0; // TODO: We need the true group here
        Optional<String> dockerImage = Optional.empty();
        return new Node(
                hostname /* id */,
                hostname /* Hostname */,
                Optional.empty() /* parent hostname */,
                new Configuration(getFlavor(clusterType, clusterId).get()),
                Status.initial(),
                Node.State.active,
                Optional.empty() /* Allocation */,
                History.empty(),
                Node.Type.tenant)                                                                   // History

                .allocate(
                        ApplicationId.from(
                                TenantName.from(tenantId),
                                ApplicationName.from(applicationId),
                                InstanceName.from(instanceId)),
                        ClusterMembership.from(
                                ClusterSpec.from(ClusterSpec.Type.from(clusterType), 
                                                 ClusterSpec.Id.from(clusterId), 
                                                 ClusterSpec.Group.from(group),
                                                 dockerImage),
                                nodeIndex),
                        creationClock.instant());
    }

    private List<Node> getNodesForCluster(String clusterType) {
        List<Node> nodes = new ArrayList<>();

        final String elementName = CLUSTER_TYPE_ELEMENT.get(clusterType);
        final NodeList clusterList = (NodeList) XmlUtils.evalXPath(servicesXml, String.format("services/%s", elementName), XPathConstants.NODESET);
        for (int i = 0; i < clusterList.getLength(); i++) {
            Element cluster = (Element) clusterList.item(i);
            String clusterId = XmlUtils.attributeOrDefault(cluster, "id", "default");

            // An empty cluster id is interpreted as 'default'
            final String partialPath = clusterId.equals("default") ?
                    String.format("services/%s[@id='default' or string-length(@id)=0]", elementName) :
                    String.format("services/%s[@id='%s']", elementName, clusterId);

            // Get all 'node' elements under 'group' or 'nodes'
            final NodeList nodeList = (NodeList) XmlUtils.evalXPath(servicesXml, String.format("%s/group/node | %s/nodes/node", partialPath, partialPath), XPathConstants.NODESET);

            for (int nodeIndex = 0; nodeIndex < nodeList.getLength(); nodeIndex++) {
                Element node = (Element) nodeList.item(nodeIndex);
                String hostname = hostMapping.get(node.getAttribute("hostalias"));
                String indexString = XmlUtils.attributeOrDefault(node, "distribution-key", "");

                final int index = !indexString.isEmpty() ? Integer.valueOf(indexString) : nodeIndex;
                nodes.add(buildNode(hostname, clusterType, clusterId, index));
            }
        }

        return nodes;
    }

    private void ensureFlavorIsDefinedForEveryCluster() {
        CLUSTER_TYPE_ELEMENT.forEach((clusterType, element) -> {
            final NodeList clusters = (NodeList) XmlUtils.evalXPath(servicesXml, "/services/" + element, XPathConstants.NODESET);

            for (int i = 0; i < clusters.getLength(); i++) {
                String clusterId = XmlUtils.attributeOrDefault((Element) clusters.item(i), "id", "default");

                if (!hasDefinedFlavor(clusterType, clusterId)) {
                    throw new RuntimeException(String.format("Flavor is not defined for %s.%s\n", clusterType, clusterId));
                }
            }
        });
    }

    private static String byteArrayToUTF8(byte[] array) {
        return Charset.forName("UTF-8").decode(ByteBuffer.wrap(array)).toString();
    }

}
