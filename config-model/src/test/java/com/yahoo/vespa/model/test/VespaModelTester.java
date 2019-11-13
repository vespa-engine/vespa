// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.test;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.model.ConfigModelRegistry;
import com.yahoo.config.model.NullConfigModelRegistry;
import com.yahoo.config.model.api.HostProvisioner;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.config.model.provision.Host;
import com.yahoo.config.model.provision.Hosts;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.model.provision.SingleNodeProvisioner;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.test.utils.ApplicationPackageUtils;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithMockPkg;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Helper class which sets up a system with multiple hosts.
 * Usage:
 * <code>
 *     VespaModelTester tester = new VespaModelTester();
 *     tester.addHosts(count, flavor);
 *     ... add more nodes
 *     VespaModel model = tester.createModel(servicesString);
 *     ... assert on model
 * </code>
 * 
 * @author bratseth
 */
public class VespaModelTester {

    private final ConfigModelRegistry configModelRegistry;

    private boolean hosted = true;
    private Map<NodeResources, Collection<Host>> hostsByResources = new HashMap<>();
    private ApplicationId applicationId = ApplicationId.defaultId();
    private boolean useDedicatedNodeForLogserver = false;

    public VespaModelTester() {
        this(new NullConfigModelRegistry());
    }
    
    public VespaModelTester(ConfigModelRegistry configModelRegistry) {
        this.configModelRegistry = configModelRegistry;
    }
    
    /** Adds some nodes with resources 1, 3, 9 */
    public Hosts addHosts(int count) { return addHosts(new NodeResources(1, 3, 9, 1), count); }

    public Hosts addHosts(NodeResources resources, int count) {
        return addHosts(Optional.of(new Flavor(resources)), resources, count);
    }

    private Hosts addHosts(Optional<Flavor> flavor, NodeResources resources, int count) {
        List<Host> hosts = new ArrayList<>();
        
        for (int i = 0; i < count; ++i) {
            // Let host names sort in the opposite order of the order the hosts are added
            // This allows us to test index vs. name order selection when subsets of hosts are selected from a cluster
            // (for e.g cluster controllers and slobrok nodes)
            String hostname = String.format("%s-%02d",
                                            "node" + "-" + Math.round(resources.vcpu()) +
                                                     "-" + Math.round(resources.memoryGb()) +
                                                     "-" + Math.round(resources.diskGb()),
                                            count - i);
            hosts.add(new Host(hostname, ImmutableList.of(), flavor));
        }
        this.hostsByResources.put(resources, hosts);

        if (hosts.size() > 100)
            throw new IllegalStateException("The host naming scheme is nameNN. To test more than 100 hosts, change to nameNNN");
        return new Hosts(hosts);
    }

    /** Sets whether this sets up a model for a hosted system. Default: true */
    public void setHosted(boolean hosted) { this.hosted = hosted; }

    /** Sets the tenant, application name, and instance name of the model being built. */
    public void setApplicationId(String tenant, String applicationName, String instanceName) {
        applicationId = ApplicationId.from(tenant, applicationName, instanceName);
    }

    public void useDedicatedNodeForLogserver(boolean useDedicatedNodeForLogserver) {
        this.useDedicatedNodeForLogserver = useDedicatedNodeForLogserver;
    }

    /** Creates a model which uses 0 as start index and fails on out of capacity */
    public VespaModel createModel(String services, String ... retiredHostNames) {
        return createModel(Zone.defaultZone(), services, true, retiredHostNames);
    }

    /** Creates a model which uses 0 as start index */
    public VespaModel createModel(String services, boolean failOnOutOfCapacity, String ... retiredHostNames) {
        return createModel(Zone.defaultZone(), services, failOnOutOfCapacity, 0, retiredHostNames);
    }

    /** Creates a model which uses 0 as start index */
    public VespaModel createModel(String services, boolean failOnOutOfCapacity, int startIndexForClusters, String ... retiredHostNames) {
        return createModel(Zone.defaultZone(), services, failOnOutOfCapacity, startIndexForClusters, retiredHostNames);
    }

    /** Creates a model which uses 0 as start index */
    public VespaModel createModel(Zone zone, String services, boolean failOnOutOfCapacity, String ... retiredHostNames) {
        return createModel(zone, services, failOnOutOfCapacity, 0, retiredHostNames);
    }

    /**
     * Creates a model using the hosts already added to this
     *
     * @param services the services xml string
     * @param failOnOutOfCapacity whether we should get an exception when not enough hosts of the requested flavor
     *        is available or if we should just silently receive a smaller allocation
     * @return the resulting model
     */
    public VespaModel createModel(Zone zone, String services, boolean failOnOutOfCapacity, int startIndexForClusters, String ... retiredHostNames) {
        VespaModelCreatorWithMockPkg modelCreatorWithMockPkg = new VespaModelCreatorWithMockPkg(null, services, ApplicationPackageUtils.generateSearchDefinition("type1"));
        ApplicationPackage appPkg = modelCreatorWithMockPkg.appPkg;

        HostProvisioner provisioner = hosted ? 
                                      new InMemoryProvisioner(hostsByResources, failOnOutOfCapacity, startIndexForClusters, retiredHostNames) :
                                      new SingleNodeProvisioner();

        TestProperties properties = new TestProperties()
                .setMultitenant(true)
                .setHostedVespa(hosted)
                .setApplicationId(applicationId)
                .setUseDedicatedNodeForLogserver(useDedicatedNodeForLogserver);

        DeployState deployState = new DeployState.Builder()
                .applicationPackage(appPkg)
                .modelHostProvisioner(provisioner)
                .properties(properties)
                .zone(zone)
                .build();
        return modelCreatorWithMockPkg.create(false, deployState, configModelRegistry);
    }
}
