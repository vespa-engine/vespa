// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.component.Version;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.admin.*;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerModel;

import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds the admin model from a version 4 XML tag, or as a default when an admin 3 tag or no admin tag is used.
 *
 * @author bratseth
 */
public class DomAdminV4Builder extends DomAdminBuilderBase {

    private final Collection<ContainerModel> containerModels;
    private final ConfigModelContext context;

    public DomAdminV4Builder(ConfigModelContext context, boolean multitenant, List<ConfigServerSpec> configServerSpecs,
                             Collection<ContainerModel> containerModels) {
        super(context.getApplicationType(), context.getDeployState().getFileRegistry(), multitenant,
              configServerSpecs);
        this.containerModels = containerModels;
        this.context = context;
    }

    @Override
    protected void doBuildAdmin(Admin admin, Element w3cAdminElement) {
        ModelElement adminElement = new ModelElement(w3cAdminElement);
        admin.addConfigservers(getConfigServersFromSpec(admin));
        Version version = context.getDeployState().getWantedNodeVespaVersion();
        
        // Note: These two elements only exists in admin version 4.0
        // This build handles admin version 3.0 by ignoring its content (as the content is not useful)
        Optional<NodesSpecification> requestedSlobroks = 
                NodesSpecification.optionalDedicatedFromParent(adminElement.getChild("slobroks"), version);
        Optional<NodesSpecification> requestedLogservers = 
                NodesSpecification.optionalDedicatedFromParent(adminElement.getChild("logservers"), version);

        assignSlobroks(requestedSlobroks.orElse(NodesSpecification.nonDedicated(3, version)), admin);
        assignLogserver(requestedLogservers.orElse(NodesSpecification.nonDedicated(1, version)), admin);

        addLogForwarders(adminElement.getChild("logforwarding"), admin);
    }

    private void assignSlobroks(NodesSpecification nodesSpecification, Admin admin) {
        if (nodesSpecification.isDedicated()) {
            createSlobroks(admin, allocateHosts(admin.getHostSystem(), "slobroks", nodesSpecification));
        }
        else {
            createSlobroks(admin, pickContainerHosts(nodesSpecification.count(), 2));
        }
    }

    private void assignLogserver(NodesSpecification nodesSpecification, Admin admin) {
        if (nodesSpecification.count() > 1) throw new IllegalArgumentException("You can only request a single log server");

        if (nodesSpecification.isDedicated()) {
            createLogserver(admin, allocateHosts(admin.getHostSystem(), "logserver", nodesSpecification));
        }
        else {
            if (containerModels.iterator().hasNext())
                createLogserver(admin, sortedContainerHostsFrom(containerModels.iterator().next(), nodesSpecification.count(), false));
        }
    }

    private Collection<HostResource> allocateHosts(HostSystem hostSystem, String clusterId, NodesSpecification nodesSpecification) {
        return nodesSpecification.provision(hostSystem, 
                                            ClusterSpec.Type.admin, 
                                            ClusterSpec.Id.from(clusterId), 
                                            context.getDeployLogger()).keySet();
    }

    /**
     * Returns a list of container hosts to use for an auxiliary cluster.
     * The list returns the same nodes on each invocation given the same available nodes.
     *
     * @param count the desired number of nodes. More nodes may be returned to ensure a smooth transition
     *        on topology changes, and less nodes may be returned if fewer are available
     * @param minHostsPerContainerCluster the desired number of hosts per cluster
     */
    private List<HostResource> pickContainerHosts(int count, int minHostsPerContainerCluster) {
        // Pick from all container clusters to make sure we don't lose all nodes at once if some clusters are removed.
        // This will overshoot the desired size (due to ceil and picking at least one node per cluster).
        List<HostResource> picked = new ArrayList<>();
        for (ContainerModel containerModel : containerModels)
            picked.addAll(pickContainerHostsFrom(containerModel,
                                                 (int) Math.max(minHostsPerContainerCluster,
                                                                Math.ceil((double) count / containerModels.size()))));
        return picked;
    }

    private List<HostResource> pickContainerHostsFrom(ContainerModel model, int count) {
        boolean retired = true;
        List<HostResource> picked = sortedContainerHostsFrom(model, count, !retired);

        // if we can return multiple hosts, include retired nodes which would have been picked before
        // (probably - assuming all previous nodes were retired, which is always true for a single cluster
        // at the moment (Sept 2015)) to ensure a smoother transition between the old and new topology
        // by including both new and old nodes during the retirement period
        picked.addAll(sortedContainerHostsFrom(model, count, retired));

        return picked;
    }

    /** Returns the count first containers in the current model having isRetired set to the given value */
    private List<HostResource> sortedContainerHostsFrom(ContainerModel model, int count, boolean retired) {
        List<HostResource> hosts = model.getCluster().getContainers().stream()
                                                                     .filter(container -> retired == container.isRetired())
                                                                     .map(Container::getHostResource)
                                                                     .collect(Collectors.toList());
        return HostResource.pickHosts(hosts, count, 1);
    }

    private void createLogserver(Admin admin, Collection<HostResource> hosts) {
        if (hosts.isEmpty()) return; // No log server can be created (and none is needed)
        Logserver logserver = new Logserver(admin);
        logserver.setHostResource(hosts.iterator().next());
        admin.setLogserver(logserver);
        logserver.initService();
    }

    private void createSlobroks(Admin admin, Collection<HostResource> hosts) {
        if (hosts.isEmpty()) return; // No slobroks can be created (and none are needed)
        List<Slobrok> slobroks = new ArrayList<>();
        int index = 0;
        for (HostResource host : hosts) {
            Slobrok slobrok = new Slobrok(admin, index++);
            slobrok.setHostResource(host);
            slobroks.add(slobrok);
            slobrok.initService();
        }
        admin.addSlobroks(slobroks);
    }

}
