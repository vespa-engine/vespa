// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import ai.vespa.http.DomainName;
import com.google.common.collect.Iterators;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.Cloud;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.ZoneEndpoint.AccessType;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.config.provision.exception.LoadBalancerServiceException;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.transaction.NestedTransaction;
import com.yahoo.vespa.flags.InMemoryFlagSource;
import com.yahoo.vespa.flags.PermanentFlags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancer;
import com.yahoo.vespa.hosted.provision.lb.LoadBalancerList;
import com.yahoo.vespa.hosted.provision.lb.Real;
import com.yahoo.vespa.hosted.provision.maintenance.LoadBalancerExpirer;
import com.yahoo.vespa.hosted.provision.maintenance.TestMetric;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.ClusterSpec.Type.combined;
import static com.yahoo.config.provision.ClusterSpec.Type.container;
import static com.yahoo.vespa.hosted.provision.lb.LoadBalancerSpec.preProvisionOwner;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author mpolden
 */
public class LoadBalancerProvisionerTest {

    private final ApplicationId app1 = ApplicationId.from("tenant1", "application1", "default");
    private final ApplicationId app2 = ApplicationId.from("tenant2", "application2", "default");
    private final NodeResources nodeResources = new NodeResources(2, 4, 10, 0.3);

    private final InMemoryFlagSource flagSource = new InMemoryFlagSource();
    private final ProvisioningTester tester = new ProvisioningTester.Builder().flagSource(flagSource)
            .zone(new Zone(Cloud.builder().allowEnclave(true).account(CloudAccount.from("001122334455")).build(),
                           SystemName.main, Environment.prod, RegionName.defaultName())).build();

    @Test
    public void provision_load_balancer() {
        Supplier<List<LoadBalancer>> lbApp1 = () -> tester.nodeRepository().loadBalancers().list(app1).asList();
        Supplier<List<LoadBalancer>> lbApp2 = () -> tester.nodeRepository().loadBalancers().list(app2).asList();
        ClusterSpec.Id containerCluster = ClusterSpec.Id.from("qrs1");
        ClusterSpec.Id contentCluster = ClusterSpec.Id.from("content");

        // Provision a load balancer for each application
        var nodes = prepare(app1,
                            clusterRequest(container, containerCluster),
                            clusterRequest(ClusterSpec.Type.content, contentCluster));
        assertEquals(1, lbApp1.get().size());
        assertEquals("Prepare provisions load balancer without any nodes", 0, lbApp1.get().get(0).instance().get().reals().size());
        tester.activate(app1, nodes);
        assertEquals("Activate configures load balancer with reserved nodes", 2, lbApp1.get().get(0).instance().get().reals().size());
        tester.activate(app2, prepare(app2, clusterRequest(container, containerCluster)));
        assertEquals(1, lbApp2.get().size());
        assertReals(app1, containerCluster, Node.State.active);
        assertReals(app2, containerCluster, Node.State.active);

        // Reals are configured after activation
        assertEquals(app1, lbApp1.get().get(0).id().application());
        assertEquals(containerCluster, lbApp1.get().get(0).id().cluster());
        assertEquals(Collections.singleton(4443), lbApp1.get().get(0).instance().get().ports());
        assertEquals("127.0.0.1", get(lbApp1.get().get(0).instance().get().reals(), 0).ipAddress());
        assertEquals(4443, get(lbApp1.get().get(0).instance().get().reals(), 0).port());
        assertEquals("127.0.0.2", get(lbApp1.get().get(0).instance().get().reals(), 1).ipAddress());
        assertEquals(4443, get(lbApp1.get().get(0).instance().get().reals(), 1).port());

        // A container is failed
        Supplier<NodeList> containers = () -> tester.getNodes(app1).container();
        Node toFail = containers.get().first().get();
        tester.nodeRepository().nodes().fail(toFail.hostname(), Agent.system, this.getClass().getSimpleName());

        // Redeploying replaces failed node and removes it from load balancer
        tester.activate(app1, prepare(app1,
                                      clusterRequest(container, containerCluster),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        LoadBalancer loadBalancer = tester.nodeRepository().loadBalancers().list(app1).asList().get(0);
        assertEquals(2, loadBalancer.instance().get().reals().size());
        assertTrue("Failed node is removed", loadBalancer.instance().get().reals().stream()
                                                         .map(Real::hostname)
                                                         .map(DomainName::value)
                                                         .noneMatch(hostname -> hostname.equals(toFail.hostname())));
        assertEquals(containers.get().state(Node.State.active).hostnames(),
                     loadBalancer.instance().get().reals().stream().map(r -> r.hostname().value()).collect(Collectors.toSet()));
        assertSame("State is unchanged", LoadBalancer.State.active, loadBalancer.state());

        // Add another container cluster to first app
        ClusterSpec.Id containerCluster2 = ClusterSpec.Id.from("qrs2");
        tester.activate(app1, prepare(app1,
                                      clusterRequest(container, containerCluster),
                                      clusterRequest(container, containerCluster2),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));

        // Load balancer is provisioned for second container cluster
        assertReals(app1, containerCluster2, Node.State.active);

        // Cluster removal deactivates relevant load balancer
        tester.activate(app1, prepare(app1, clusterRequest(container, containerCluster)));
        assertEquals(2, lbApp1.get().size());
        assertEquals("Deactivated load balancer for cluster " + containerCluster2, LoadBalancer.State.inactive,
                     lbApp1.get().stream()
                           .filter(lb -> lb.id().cluster().equals(containerCluster2))
                           .map(LoadBalancer::state)
                           .findFirst()
                           .get());
        assertEquals("Load balancer for cluster " + containerCluster + " remains active", LoadBalancer.State.active,
                     lbApp1.get().stream()
                           .filter(lb -> lb.id().cluster().equals(containerCluster))
                           .map(LoadBalancer::state)
                           .findFirst()
                           .get());

        // Entire application is removed: Nodes and load balancer are deactivated
        tester.remove(app1);
        dirtyNodesOf(app1);
        assertTrue("No nodes are allocated to " + app1, tester.nodeRepository().nodes().list(Node.State.reserved, Node.State.active).owner(app1).isEmpty());
        assertEquals(2, lbApp1.get().size());
        assertTrue("Deactivated load balancers", lbApp1.get().stream().allMatch(lb -> lb.state() == LoadBalancer.State.inactive));
        assertTrue("Load balancers for " + app2 + " remain active", lbApp2.get().stream().allMatch(lb -> lb.state() == LoadBalancer.State.active));

        // Application is redeployed with one cluster and load balancer is re-activated
        tester.activate(app1, prepare(app1,
                                      clusterRequest(container, containerCluster),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        assertSame("Re-activated load balancer for " + containerCluster, LoadBalancer.State.active,
                   lbApp1.get().stream()
                         .filter(lb -> lb.id().cluster().equals(containerCluster))
                         .map(LoadBalancer::state)
                         .findFirst()
                         .orElseThrow());

        // Next redeploy does not create a new load balancer instance because reals are unchanged
        tester.loadBalancerService().throwOnCreate(true);
        tester.activate(app1, prepare(app1,
                                      clusterRequest(container, containerCluster),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));

        // Routing is disabled through feature flag. Reals are removed on next deployment
        tester.loadBalancerService().throwOnCreate(false);
        flagSource.withBooleanFlag(PermanentFlags.DEACTIVATE_ROUTING.id(), true);
        tester.activate(app1, prepare(app1,
                                      clusterRequest(container, containerCluster),
                                      clusterRequest(ClusterSpec.Type.content, contentCluster)));
        List<LoadBalancer> activeLoadBalancers = lbApp1.get().stream()
                                                       .filter(lb -> lb.state() == LoadBalancer.State.active).toList();
        assertEquals(1, activeLoadBalancers.size());
        assertEquals(Set.of(), activeLoadBalancers.get(0).instance().get().reals());
    }

    @Test
    public void pre_provision_load_balancers() {
        flagSource.withIntFlag(PermanentFlags.PRE_PROVISIONED_LB_COUNT.id(), 2);
        LoadBalancerProvisioner provisioner = new LoadBalancerProvisioner(tester.nodeRepository(), tester.loadBalancerService());
        LoadBalancerExpirer expirer = new LoadBalancerExpirer(tester.nodeRepository(), Duration.ofDays(1), tester.loadBalancerService(), new NullMetric());
        provisioner.refreshPool();
        expirer.run();
        assertEquals(2, loadBalancers().size());
        assertEquals(2, tester.nodeRepository().loadBalancers().list(preProvisionOwner).size());

        // Provision a load balancer when the pool has two entries.
        ClusterSpec.Id containerCluster = ClusterSpec.Id.from("qrs");
        prepare(app1, clusterRequest(container, containerCluster));
        List<LoadBalancer> loadBalancers = tester.nodeRepository().loadBalancers().list(app1).asList();
        assertEquals(1, loadBalancers.size());
        assertEquals(1, tester.nodeRepository().loadBalancers().list(preProvisionOwner).asList().size());
        assertEquals(":" + preProvisionOwner.serializedForm() + ":1:", loadBalancers.get(0).idSeed());

        // Shrink pool to 0 entries.
        flagSource.withIntFlag(PermanentFlags.PRE_PROVISIONED_LB_COUNT.id(), 0);
        provisioner.refreshPool();
        expirer.run();
        assertEquals(loadBalancers.stream().map(LoadBalancer::id).toList(),
                     loadBalancers().mapToList(LoadBalancer::id));

        // Increase pool to 1 entry again. Creating an LB fails; the slot and idSeed are reused on retry.
        tester.loadBalancerService().throwOnCreate(true);
        flagSource.withIntFlag(PermanentFlags.PRE_PROVISIONED_LB_COUNT.id(), 1);
        assertEquals("Did not expect a new load balancer to be created",
                     assertThrows(IllegalStateException.class, provisioner::refreshPool).getMessage());
        tester.loadBalancerService().throwOnCreate(false);
        provisioner.refreshPool();
        assertEquals(List.of(":" + preProvisionOwner.serializedForm() + ":3:"),
                     tester.nodeRepository().loadBalancers().list(preProvisionOwner)
                           .mapToList(LoadBalancer::idSeed));
    }

    @Test
    public void provision_load_balancers_with_dynamic_node_provisioning() {
        NodeResources resources = new NodeResources(1, 4, 10, 0.3);
        tester.makeReadyHosts(2, resources);
        tester.activateTenantHosts();
        var nodes = tester.prepare(app1, clusterRequest(container, ClusterSpec.Id.from("qrs")), 2 , 1, resources);
        Supplier<LoadBalancer> lb = () -> tester.nodeRepository().loadBalancers().list(app1).asList().get(0);
        assertEquals("Load balancer provisioned with empty reals", Set.of(), tester.loadBalancerService().instances().get(lb.get().id()).reals());
        assignIps(tester.nodeRepository().nodes().list().owner(app1));
        tester.activate(app1, nodes);
        assertNotEquals("Load balancer is reconfigured with reals", Set.of(), tester.loadBalancerService().instances().get(lb.get().id()).reals());

        // Application is removed, nodes are deleted and load balancer is deactivated
        tester.remove(app1);
        NestedTransaction transaction = new NestedTransaction();
        tester.nodeRepository().database().removeNodes(tester.nodeRepository().nodes().list().nodeType(NodeType.tenant).asList(), transaction);
        transaction.commit();
        assertTrue("Nodes are deleted", tester.nodeRepository().nodes().list().nodeType(NodeType.tenant).isEmpty());
        assertSame("Load balancer is deactivated", LoadBalancer.State.inactive, lb.get().state());

        // Load balancer reals are removed.
        new LoadBalancerExpirer(tester.nodeRepository(), Duration.ofDays(1), tester.loadBalancerService(), new NullMetric()).run();

        // Application is redeployed
        nodes = tester.prepare(app1, clusterRequest(container, ClusterSpec.Id.from("qrs")), 2, 1, resources);
        assertEquals("Load balancer is reconfigured with empty reals", Set.of(), tester.loadBalancerService().instances().get(lb.get().id()).reals());
        assignIps(tester.nodeRepository().nodes().list().owner(app1));
        tester.activate(app1, nodes);
        assertNotEquals("Load balancer is reconfigured with reals", Set.of(), tester.loadBalancerService().instances().get(lb.get().id()).reals());
    }

    @Test
    public void does_not_provision_load_balancers_for_non_tenant_node_type() {
        var tenantHostApp = ProvisioningTester.infraApplication(NodeType.host);
        tester.prepareAndActivateInfraApplication(tenantHostApp);
        assertTrue("No load balancer provisioned", tester.loadBalancerService().instances().isEmpty());
        assertEquals(List.of(), tester.nodeRepository().loadBalancers().list(tenantHostApp.getApplicationId()).asList());
    }

    @Test
    public void does_not_provision_load_balancers_for_non_container_cluster() {
        tester.activate(app1, prepare(app1, clusterRequest(ClusterSpec.Type.content,
                                                           ClusterSpec.Id.from("tenant-host"))));
        assertTrue("No load balancer provisioned", tester.loadBalancerService().instances().isEmpty());
        assertEquals(List.of(), tester.nodeRepository().loadBalancers().list(app1).asList());
    }

    @Test
    public void provision_load_balancer_combined_cluster() {
        Supplier<List<LoadBalancer>> lbs = () -> tester.nodeRepository().loadBalancers().list(app1).asList();
        var combinedId = ClusterSpec.Id.from("container1");
        var nodes = prepare(app1, clusterRequest(combined, ClusterSpec.Id.from("content1"), Optional.of(combinedId), ZoneEndpoint.defaultEndpoint));
        assertEquals(1, lbs.get().size());
        assertEquals("Prepare provisions load balancer without reserved nodes", 0, lbs.get().get(0).instance().get().reals().size());
        tester.activate(app1, nodes);
        assertEquals("Activate configures load balancer with reserved nodes", 2, lbs.get().get(0).instance().get().reals().size());
        assertSame(LoadBalancer.State.active, lbs.get().get(0).state());
        assertEquals(combinedId, lbs.get().get(0).id().cluster());
    }

    @Test
    public void provision_load_balancer_config_server_cluster() {
        provisionInfrastructureLoadBalancer(NodeType.config);
    }

    @Test
    public void provision_load_balancer_controller_cluster() {
        provisionInfrastructureLoadBalancer(NodeType.controller);
    }

    @Test
    public void reject_load_balancers_with_clashing_names() {
        ApplicationId instance1 = ApplicationId.from("t1", "a1", "default");
        ApplicationId instance2 = ApplicationId.from("t1", "a1", "dev");
        ApplicationId instance3 = ApplicationId.from("t1", "a1", "qrs");
        ClusterSpec.Id devCluster = ClusterSpec.Id.from("dev");
        ClusterSpec.Id defaultCluster = ClusterSpec.Id.from("default");

        // instance1 is deployed
        tester.activate(instance1, prepare(instance1, clusterRequest(container, devCluster)));

        // instance2 clashes because instance name matches instance1 cluster name
        try {
            prepare(instance2, clusterRequest(container, defaultCluster));
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {
        }

        // instance2 changes cluster name and does not clash
        tester.activate(instance2, prepare(instance2, clusterRequest(container, ClusterSpec.Id.from("qrs"))));

        // instance3 does not clash
        tester.activate(instance3, prepare(instance3, clusterRequest(container, defaultCluster)));
    }

    @Test
    public void provisioning_load_balancer_fails_initially() {
        Supplier<List<LoadBalancer>> lbs = () -> tester.nodeRepository().loadBalancers().list(app1).asList();
        ClusterSpec.Id cluster = ClusterSpec.Id.from("qrs1");

        // Provisioning load balancer fails on deployment
        tester.loadBalancerService().throwOnCreate(true);
        try {
            prepare(app1, clusterRequest(container, cluster));
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {}
        List<LoadBalancer> loadBalancers = lbs.get();
        assertEquals(1, loadBalancers.size());
        assertSame(LoadBalancer.State.reserved, loadBalancers.get(0).state());
        assertTrue("Load balancer has no instance", loadBalancers.get(0).instance().isEmpty());

        // Next deployment succeeds
        tester.loadBalancerService().throwOnCreate(false);
        Set<HostSpec> nodes = prepare(app1, clusterRequest(container, cluster));
        loadBalancers = lbs.get();
        assertSame(LoadBalancer.State.reserved, loadBalancers.get(0).state());
        assertTrue("Load balancer has instance", loadBalancers.get(0).instance().isPresent());
        tester.activate(app1, nodes);
        loadBalancers = lbs.get();
        assertSame(LoadBalancer.State.active, loadBalancers.get(0).state());
        assertTrue("Load balancer has instance", loadBalancers.get(0).instance().isPresent());

        // Reconfiguration of load balancer fails on next prepare, but instance is preserved
        tester.loadBalancerService().throwOnCreate(true);
        ZoneEndpoint settings = new ZoneEndpoint(true, true, List.of(new AllowedUrn(AccessType.awsPrivateLink, "alice"), new AllowedUrn(AccessType.gcpServiceConnect, "bob")));
        try {
            prepare(app1, clusterRequest(container, cluster, Optional.empty(), settings));
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {
        }
        assertSame(LoadBalancer.State.active, loadBalancers.get(0).state());
        assertTrue("Load balancer has instance", loadBalancers.get(0).instance().isPresent());
    }

    @Test
    public void provisioning_load_balancer_for_unsupported_cluster_fails_gracefully() {
        tester.loadBalancerService().supportsProvisioning(false);
        tester.activate(app1, prepare(app1, clusterRequest(container, ClusterSpec.Id.from("qrs"))));
        assertTrue("No load balancer provisioned", tester.nodeRepository().loadBalancers().list(app1).asList().isEmpty());
    }

    @Test
    public void load_balancer_targets_newly_active_nodes() {
        ClusterSpec.Id container1 = ClusterSpec.Id.from("c1");
        // Initial deployment
        {
            Capacity capacity1 = Capacity.from(new ClusterResources(3, 1, nodeResources));
            Set<HostSpec> preparedHosts = prepare(app1, capacity1, clusterRequest(container, container1));
            tester.activate(app1, preparedHosts);
        }
        assertReals(app1, container1, Node.State.active);

        // Next deployment removes a node
        {
            Capacity capacity1 = Capacity.from(new ClusterResources(2, 1, nodeResources));
            Set<HostSpec> preparedHosts = prepare(app1, capacity1, clusterRequest(container, container1));
            tester.activate(app1, preparedHosts);
        }
        assertReals(app1, container1, Node.State.active);
    }

    @Test
    public void load_balancer_with_custom_settings() {
        ClusterResources resources = new ClusterResources(3, 1, nodeResources);
        Capacity capacity = Capacity.from(resources, resources, IntRange.empty(), false, true, Optional.of(CloudAccount.empty), ClusterInfo.empty());
        tester.activate(app1, prepare(app1, capacity, clusterRequest(container, ClusterSpec.Id.from("c1"))));
        LoadBalancerList loadBalancers = loadBalancers();
        assertEquals(1, loadBalancers.size());
        assertEquals(ZoneEndpoint.defaultEndpoint, loadBalancers.first().get().instance().get().settings());

        // Next deployment contains new settings
        ZoneEndpoint settings = new ZoneEndpoint(true, true, List.of(new AllowedUrn(AccessType.awsPrivateLink, "alice"), new AllowedUrn(AccessType.gcpServiceConnect, "bob")));
        tester.activate(app1, prepare(app1, capacity, clusterRequest(container, ClusterSpec.Id.from("c1"), Optional.empty(), settings)));
        loadBalancers = loadBalancers();
        assertEquals(1, loadBalancers.size());
        assertEquals(settings, loadBalancers.first().get().instance().get().settings());
    }

    @Test
    public void load_balancer_with_changing_visibility() {
        ClusterResources resources = new ClusterResources(3, 1, nodeResources);
        Capacity capacity = Capacity.from(resources, resources, IntRange.empty(), false, true, Optional.of(CloudAccount.empty), ClusterInfo.empty());
        tester.activate(app1, prepare(app1, capacity, clusterRequest(container, ClusterSpec.Id.from("c1"))));
        LoadBalancerList loadBalancers = loadBalancers();
        assertEquals(1, loadBalancers.size());
        assertEquals(ZoneEndpoint.defaultEndpoint, loadBalancers.first().get().instance().get().settings());

        // Next deployment has only a private endpoint
        ZoneEndpoint settings = new ZoneEndpoint(false, true, List.of(new AllowedUrn(AccessType.awsPrivateLink, "alice"), new AllowedUrn(AccessType.gcpServiceConnect, "bob")));
        assertEquals("Could not (re)configure load balancer tenant1:application1:default:c1 due to change in load balancer visibility." +
                             " Current load balancer settings: ZoneEndpoint{isPublicEndpoint=true, isPrivateEndpoint=false, allowedUrns=[]}," +
                             " zone endpoint settings: ZoneEndpoint{isPublicEndpoint=false, isPrivateEndpoint=true, allowedUrns=['alice' through 'aws-private-link', 'bob' through 'gcp-service-connect']}." +
                             " The operation will be retried on next deployment",
                     assertThrows(LoadBalancerServiceException.class,
                                  () -> prepare(app1, capacity, clusterRequest(container, ClusterSpec.Id.from("c1"), Optional.empty(), settings)))
                             .getMessage());

        // Existing LB is removed
        loadBalancers = loadBalancers();
        assertEquals(1, loadBalancers.size());
        assertSame(LoadBalancer.State.removable, loadBalancers.first().get().state());
        new LoadBalancerExpirer(tester.nodeRepository(),
                                Duration.ofDays(1),
                                tester.loadBalancerService(),
                                new TestMetric())
                .run();
        assertEquals(0, loadBalancers().in(LoadBalancer.State.removable).size());

        // Next deployment provisions a new LB
        tester.activate(app1, prepare(app1, capacity, clusterRequest(container, ClusterSpec.Id.from("c1"), Optional.empty(), settings)));
        loadBalancers = loadBalancers();
        assertEquals(1, loadBalancers.size());
        assertEquals(settings, loadBalancers.first().get().instance().get().settings());
    }

    @Test
    public void load_balancer_with_custom_cloud_account() {
        ClusterResources resources = new ClusterResources(3, 1, nodeResources);
        CloudAccount cloudAccount0 = CloudAccount.empty;
        {
            Capacity capacity = Capacity.from(resources, resources, IntRange.empty(), false, true, Optional.of(cloudAccount0), ClusterInfo.empty());
            tester.activate(app1, prepare(app1, capacity, clusterRequest(container, ClusterSpec.Id.from("c1"))));
        }
        LoadBalancerList loadBalancers = loadBalancers();
        assertEquals(1, loadBalancers.size());
        assertEquals(cloudAccount0, loadBalancers.first().get().instance().get().cloudAccount());

        // Changing account fails if there is an existing LB in the previous account.
        CloudAccount cloudAccount1 = CloudAccount.from("111111111111");
        Capacity capacity = Capacity.from(resources, resources, IntRange.empty(), false, true, Optional.of(cloudAccount1), ClusterInfo.empty());
        try {
            prepare(app1, capacity, clusterRequest(container, ClusterSpec.Id.from("c1")));
            fail("Expected exception");
        } catch (LoadBalancerServiceException e) {
            assertTrue(e.getMessage().contains("due to change in cloud account"));
        }

        // Existing LB and nodes are removed
        loadBalancers = loadBalancers();
        assertEquals(1, loadBalancers.size());
        assertSame(LoadBalancer.State.removable, loadBalancers.first().get().state());
        LoadBalancerExpirer expirer = new LoadBalancerExpirer(tester.nodeRepository(),
                                                              Duration.ofDays(1),
                                                              tester.loadBalancerService(),
                                                              new TestMetric());
        expirer.run();
        assertEquals(0, loadBalancers().in(LoadBalancer.State.removable).size());
        tester.deactivate(app1);
        tester.nodeRepository().nodes().list().forEach(node -> tester.nodeRepository().nodes().removeRecursively(node, true));

        // Next deployment provisions a new LB
        tester.activate(app1, prepare(app1, capacity, clusterRequest(container, ClusterSpec.Id.from("c1"))));
        loadBalancers = loadBalancers();
        assertEquals(1, loadBalancers.size());
        assertEquals(cloudAccount1, loadBalancers.first().get().instance().get().cloudAccount());
    }

    @Test
    public void container_load_balancer_with_existing_in_removable_state() {
        load_balancer_with_existing_in_removable_state(container);
    }

    @Test
    public void combined_load_balancer_with_existing_in_removable_state() {
        load_balancer_with_existing_in_removable_state(combined);
    }

    private void load_balancer_with_existing_in_removable_state(ClusterSpec.Type clusterType) {
        ClusterResources resources = new ClusterResources(3, 1, nodeResources);
        Capacity capacity = Capacity.from(resources, resources, IntRange.empty(), false, true, Optional.of(CloudAccount.empty), ClusterInfo.empty());
        ClusterSpec clusterSpec = clusterRequest(
                clusterType,
                ClusterSpec.Id.from("c1"),
                clusterType == container ? Optional.empty() : Optional.of(ClusterSpec.Id.from("c2")),
                ZoneEndpoint.defaultEndpoint);

        tester.activate(app1, prepare(app1, capacity, clusterSpec));
        assertEquals(1, loadBalancers().size());

        // Remove app, existing LB should be inactive
        tester.remove(app1);
        assertEquals(1, loadBalancers().size());
        assertSame(LoadBalancer.State.inactive, loadBalancers().first().get().state());
        var expirer = new LoadBalancerExpirer(tester.nodeRepository(),
                                Duration.ofDays(1),
                                tester.loadBalancerService(),
                                new TestMetric());
        // Expirer should not remove load balancer
        expirer.run();
        assertEquals(1, loadBalancers().in(LoadBalancer.State.inactive).size());

        tester.clock().advance(Duration.ofHours(1));

        // Prepare app, should reuse the LB, but it should still be inactive
        var hosts = prepare(app1, capacity, clusterSpec);
        assertEquals(1, loadBalancers().size());
        assertEquals(1, loadBalancers().in(LoadBalancer.State.inactive).size());

        // Run expirer, should not remove load balancer
        expirer.run();
        assertEquals(1, loadBalancers().size());
        assertEquals(1, loadBalancers().in(LoadBalancer.State.inactive).size());

        // Activate should work
        tester.activate(app1, hosts);
    }

    private void assertReals(ApplicationId application, ClusterSpec.Id cluster, Node.State... states) {
        List<LoadBalancer> loadBalancers = tester.nodeRepository().loadBalancers().list(application).cluster(cluster).asList();
        assertEquals(1, loadBalancers.size());
        List<String> reals = loadBalancers.get(0).instance().get().reals().stream()
                                          .map(real -> real.hostname().value())
                                          .sorted()
                                          .toList();
        List<String> activeNodes = tester.nodeRepository().nodes().list(states)
                                         .owner(application)
                                         .cluster(cluster)
                                         .hostnames().stream()
                                         .sorted()
                                         .toList();
        assertEquals("Load balancer targets active nodes of " + application + " in " + cluster,
                     activeNodes, reals);
    }

    private void provisionInfrastructureLoadBalancer(NodeType nodeType) {
        var infraApp = ProvisioningTester.infraApplication(nodeType);
        Supplier<List<LoadBalancer>> lbs = () -> tester.nodeRepository().loadBalancers().list(infraApp.getApplicationId()).asList();
        tester.prepareAndActivateInfraApplication(infraApp);
        var nodes = prepare(infraApp.getApplicationId(), infraApp.getCapacity(), infraApp.getClusterSpecWithVersion(Version.fromString("7.1")));
        assertEquals(1, lbs.get().size());
        tester.activate(infraApp.getApplicationId(), nodes);
        assertEquals("Prepare provisions load balancer with reserved nodes", 3, lbs.get().get(0).instance().get().reals().size());
        assertSame(LoadBalancer.State.active, lbs.get().get(0).state());
        assertEquals(infraApp.getClusterSpecId(), lbs.get().get(0).id().cluster());
    }

    private void dirtyNodesOf(ApplicationId application) {
        tester.nodeRepository().nodes().deallocate(tester.nodeRepository().nodes().list().owner(application).asList(), Agent.system, this.getClass().getSimpleName());
    }

    private Set<HostSpec> prepare(ApplicationId application, ClusterSpec... specs) {
        return prepare(application, Capacity.from(new ClusterResources(2, 1, nodeResources), false, true), specs);
    }

    private Set<HostSpec> prepare(ApplicationId application, Capacity capacity, ClusterSpec... specs) {
        int nodeCount = capacity.minResources().nodes();
        if (capacity.type().isConfigServerLike()) {
            nodeCount = 3;
        }
        List<Node> provisioned = tester.makeProvisionedNodes(specs.length * nodeCount, (index) -> "host-" + index + ".yahoo.com", new Flavor(nodeResources),
                                                             Optional.empty(), capacity.type(), 0, false,
                                                             capacity.cloudAccount().filter(acc -> !acc.isUnspecified())
                                                                     .orElse(tester.nodeRepository().zone().cloud().account()));
        tester.move(Node.State.ready, provisioned);
        Set<HostSpec> allNodes = new LinkedHashSet<>();
        for (ClusterSpec spec : specs) {
            allNodes.addAll(tester.prepare(application, spec, capacity));
        }
        return allNodes;
    }

    private void assignIps(NodeList nodes) {
        try (var lock = tester.nodeRepository().nodes().lockUnallocated()) {
            for (int i = 0; i < nodes.size(); i++) {
                tester.nodeRepository().nodes().write(nodes.asList().get(i).with(IP.Config.EMPTY.withPrimary(List.of("127.0.0." + i))), lock);
            }
        }
    }

    private LoadBalancerList loadBalancers() {
        return tester.nodeRepository().loadBalancers().list();
    }

    private static ClusterSpec clusterRequest(ClusterSpec.Type type, ClusterSpec.Id id) {
        return clusterRequest(type, id, Optional.empty(), ZoneEndpoint.defaultEndpoint);
    }

    private static ClusterSpec clusterRequest(ClusterSpec.Type type, ClusterSpec.Id id, Optional<ClusterSpec.Id> combinedId, ZoneEndpoint settings) {
        return ClusterSpec.request(type, id).vespaVersion("6.42").combinedId(combinedId).loadBalancerSettings(settings).build();
    }

    private static <T> T get(Set<T> set, int position) {
        if (!(set instanceof SortedSet)) {
            throw new IllegalArgumentException(set + " is not a sorted set");
        }
        return Iterators.get(set.iterator(), position, null);
    }

}
