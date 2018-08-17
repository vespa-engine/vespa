// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.athenz.identityprovider.api.IdentityType;
import com.yahoo.vespa.athenz.identityprovider.api.VespaUniqueInstanceId;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.testutils.MockNodeFlavors;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation.InstanceValidator.SERVICE_PROPERTIES_DOMAIN_KEY;
import static com.yahoo.vespa.hosted.athenz.instanceproviderservice.instanceconfirmation.InstanceValidator.SERVICE_PROPERTIES_SERVICE_KEY;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author valerijf
 * @author bjorncs
 * @author mortent
 */
public class InstanceValidatorTest {

    private final ApplicationId applicationId = ApplicationId.from("tenant", "application", "instance");
    private final String domain = "domain";
    private final String service = "service";

    @Test
    public void application_does_not_exist() {
        SuperModelProvider superModelProvider = mockSuperModelProvider();
        InstanceValidator instanceValidator = new InstanceValidator(null, superModelProvider, null);

        assertFalse(instanceValidator.isSameIdentityAsInServicesXml(applicationId, domain, service));
    }

    @Test
    public void application_does_not_have_domain_set() {
        SuperModelProvider superModelProvider = mockSuperModelProvider(
                mockApplicationInfo(applicationId, 5, Collections.emptyList()));
        InstanceValidator instanceValidator = new InstanceValidator(null, superModelProvider, null);

        assertFalse(instanceValidator.isSameIdentityAsInServicesXml(applicationId, domain, service));
    }

    @Test
    public void application_has_wrong_domain() {
        ServiceInfo serviceInfo = new ServiceInfo("serviceName", "type", Collections.emptyList(),
                                                  Collections.singletonMap(SERVICE_PROPERTIES_DOMAIN_KEY, "not-domain"), "confId", "hostName");

        SuperModelProvider superModelProvider = mockSuperModelProvider(
                mockApplicationInfo(applicationId, 5, Collections.singletonList(serviceInfo)));
        InstanceValidator instanceValidator = new InstanceValidator(null, superModelProvider, null);

        assertFalse(instanceValidator.isSameIdentityAsInServicesXml(applicationId, domain, service));
    }

    @Test
    public void application_has_same_domain_and_service() {
        Map<String, String> properties = new HashMap<>();
        properties.put(SERVICE_PROPERTIES_DOMAIN_KEY, domain);
        properties.put(SERVICE_PROPERTIES_SERVICE_KEY, service);

        ServiceInfo serviceInfo = new ServiceInfo("serviceName", "type", Collections.emptyList(),
                                                  properties, "confId", "hostName");

        SuperModelProvider superModelProvider = mockSuperModelProvider(
                mockApplicationInfo(applicationId, 5, Collections.singletonList(serviceInfo)));
        InstanceValidator instanceValidator = new InstanceValidator(null, superModelProvider, null);

        assertTrue(instanceValidator.isSameIdentityAsInServicesXml(applicationId, domain, service));
    }

    @Test
    public void accepts_valid_refresh_requests() {
        NodeRepository nodeRepository = mock(NodeRepository.class);
        InstanceValidator instanceValidator = new InstanceValidator(null, null, nodeRepository);

        List<Node> nodeList = createNodes(10);
        Node node = nodeList.get(0);
        nodeList = allocateNode(nodeList, node, applicationId);
        when(nodeRepository.getNodes()).thenReturn(nodeList);
        String nodeIp = node.ipAddresses().stream().findAny().orElseThrow(() -> new RuntimeException("No ipaddress for mocked node"));
        InstanceConfirmation instanceConfirmation = createRefreshInstanceConfirmation(ImmutableList.of(nodeIp), applicationId);

        assertTrue(instanceValidator.isValidRefresh(instanceConfirmation));
    }

    @Test
    public void rejects_refresh_on_ip_mismatch() {
        NodeRepository nodeRepository = mock(NodeRepository.class);
        InstanceValidator instanceValidator = new InstanceValidator(null, null, nodeRepository);

        List<Node> nodeList = createNodes(10);
        Node node = nodeList.get(0);
        nodeList = allocateNode(nodeList, node, applicationId);
        when(nodeRepository.getNodes()).thenReturn(nodeList);
        String nodeIp = node.ipAddresses().stream().findAny().orElseThrow(() -> new RuntimeException("No ipaddress for mocked node"));

        // Add invalid ip to list of ip addresses
        InstanceConfirmation instanceConfirmation = createRefreshInstanceConfirmation(ImmutableList.of(nodeIp, "::ff"), applicationId);

        assertFalse(instanceValidator.isValidRefresh(instanceConfirmation));
    }

    @Test
    public void rejects_refresh_when_node_is_not_allocated() {
        NodeRepository nodeRepository = mock(NodeRepository.class);
        InstanceValidator instanceValidator = new InstanceValidator(null, null, nodeRepository);

        List<Node> nodeList = createNodes(10);
        when(nodeRepository.getNodes()).thenReturn(nodeList);
        InstanceConfirmation instanceConfirmation = createRefreshInstanceConfirmation(ImmutableList.of("::11"), applicationId);

        assertFalse(instanceValidator.isValidRefresh(instanceConfirmation));

    }

    private InstanceConfirmation createRefreshInstanceConfirmation(List<String> ips, ApplicationId applicationId) {
        InstanceConfirmation instanceConfirmation = new InstanceConfirmation(
                "vespa.vespa.cd.provider_dev_us-north-1",
                "vespa.vespa.cd",
                "tenant",
                null);

        instanceConfirmation.set("sanIP", String.join(",", ips));
        VespaUniqueInstanceId vespaUniqueInstanceId = new VespaUniqueInstanceId(0, "default", applicationId.instance().value(), applicationId.application().value(), applicationId.tenant().value(), "us-north-1", "dev", IdentityType.NODE);
        instanceConfirmation.set("sanDNS", vespaUniqueInstanceId.asDottedString() + ".instanceid.athenz.dev-us-north-1.vespa.yahoo.cloud");
        return instanceConfirmation;
    }

    private SuperModelProvider mockSuperModelProvider(ApplicationInfo... appInfos) {
        SuperModel superModel = new SuperModel(Stream.of(appInfos)
                                                       .collect(Collectors.groupingBy(
                                                               appInfo -> appInfo.getApplicationId().tenant(),
                                                               Collectors.toMap(
                                                                       ApplicationInfo::getApplicationId,
                                                                       Function.identity()
                                                               )
                                                       )));

        SuperModelProvider superModelProvider = mock(SuperModelProvider.class);
        when(superModelProvider.getSuperModel()).thenReturn(superModel);
        return superModelProvider;
    }

    private ApplicationInfo mockApplicationInfo(ApplicationId appId, int numHosts, List<ServiceInfo> serviceInfo) {
        List<HostInfo> hosts = IntStream.range(0, numHosts)
                .mapToObj(i -> new HostInfo("host-" + i + "." + appId.toShortString() + ".yahoo.com", serviceInfo))
                .collect(Collectors.toList());

        Model model = mock(Model.class);
        when(model.getHosts()).thenReturn(hosts);

        return new ApplicationInfo(appId, 0, model);
    }

    private List<Node> createNodes(int num) {
        MockNodeFlavors flavors = new MockNodeFlavors();
        List<Node> nodeList = new ArrayList<>();
        for (int i = 0; i < num; i++) {
            Node node = Node.create("foo" + i, ImmutableSet.of("::1" + i, "::2" + i, "::3" + i), Collections.emptySet(), "foo" + i, Optional.empty(), flavors.getFlavorOrThrow("default"), NodeType.tenant);
            nodeList.add(node);
        }
        return nodeList;
    }

    private List<Node> allocateNode(List<Node> nodeList, Node node, ApplicationId applicationId) {
        nodeList.removeIf(n -> n.openStackId().equals(node.openStackId()));
        nodeList.add(node.allocate(applicationId, ClusterMembership.from("container/default/0/0", Version.fromString("6.123.4")), Instant.now()));
        return nodeList;
    }
}
