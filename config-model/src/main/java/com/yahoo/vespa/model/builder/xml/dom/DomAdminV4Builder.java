// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.Logserver;
import com.yahoo.vespa.model.admin.LogserverContainer;
import com.yahoo.vespa.model.admin.LogserverContainerCluster;
import com.yahoo.vespa.model.admin.Slobrok;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerModel;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
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
        super(context.getApplicationType(), multitenant, configServerSpecs);
        this.containerModels = containerModels;
        this.context = context;
    }

    @Override
    protected void doBuildAdmin(DeployState deployState, Admin admin, Element w3cAdminElement) {
        ModelElement adminElement = new ModelElement(w3cAdminElement);
        admin.addConfigservers(getConfigServersFromSpec(deployState, admin));

        // Note: These two elements only exists in admin version 4.0
        // This build handles admin version 3.0 by ignoring its content (as the content is not useful)
        Optional<NodesSpecification> requestedSlobroks = 
                NodesSpecification.optionalDedicatedFromParent(adminElement.child("slobroks"), context);
        Optional<NodesSpecification> requestedLogservers = 
                NodesSpecification.optionalDedicatedFromParent(adminElement.child("logservers"), context);

        assignSlobroks(deployState, requestedSlobroks.orElse(NodesSpecification.nonDedicated(3, context)), admin);
        assignLogserver(deployState, requestedLogservers.orElse(createNodesSpecificationForLogserver()), admin);

        addLogForwarders(adminElement.child("logforwarding"), admin);
        addLoggingSpecs(adminElement.child("logging"), admin);
    }

    private void assignSlobroks(DeployState deployState, NodesSpecification nodesSpecification, Admin admin) {
        if (nodesSpecification.isDedicated()) {
            createSlobroks(deployState,
                           admin,
                           allocateHosts(admin.hostSystem(), "slobroks", nodesSpecification));
        }
        else { // These will be removed later, if an admin cluster (for cluster controllers) is assigned
            createSlobroks(deployState,
                           admin,
                           pickContainerHostsForSlobrok(nodesSpecification.minResources().nodes(), 2));
        }
    }

    private void assignLogserver(DeployState deployState, NodesSpecification nodesSpecification, Admin admin) {
        if (nodesSpecification.minResources().nodes() > 1)
            throw new IllegalArgumentException("You can only request a single log server");

        Collection<HostResource> hosts = List.of();
        if (nodesSpecification.isDedicated())
            hosts = allocateHosts(admin.hostSystem(), "logserver", nodesSpecification);
        else if (containerModels.iterator().hasNext())
            hosts = sortedContainerHostsFrom(containerModels.iterator().next(), nodesSpecification.minResources().nodes(), false);
        else
            context.getDeployLogger().logApplicationPackage(Level.INFO, "No container host available to use for running logserver");

        if (hosts.isEmpty()) return; // No log server can be created (and none is needed)

        Logserver logserver = createLogserver(deployState, admin, hosts);
        if (nodesSpecification.isDedicated() || deployState.isHosted() && deployState.getProperties().applicationId().instance().isTester())
            createContainerOnLogserverHost(deployState, admin, logserver.getHostResource());
    }

    private NodesSpecification createNodesSpecificationForLogserver() {
        DeployState deployState = context.getDeployState();
        if (     deployState.getProperties().useDedicatedNodeForLogserver()
            &&   context.getApplicationType() == ConfigModelContext.ApplicationType.DEFAULT
            &&   deployState.isHosted()
            && ! deployState.getProperties().applicationId().instance().isTester())
            return NodesSpecification.dedicated(1, context);
        else
            return NodesSpecification.nonDedicated(1, context);
    }

    // Creates a container cluster 'logs' with a container on the logserver host
    // that has a handler for getting logs
    private void createContainerOnLogserverHost(DeployState deployState, Admin admin, HostResource hostResource) {
        LogserverContainerCluster logServerCluster = new LogserverContainerCluster(admin, "logs", deployState);
        ContainerModel logserverClusterModel = new ContainerModel(context.withParent(admin).withId(logServerCluster.getSubId()));
        logserverClusterModel.setCluster(logServerCluster);

        LogserverContainer container = new LogserverContainer(logServerCluster, deployState);
        container.setHostResource(hostResource);
        container.initService(deployState);
        logServerCluster.addContainer(container);
        admin.addAndInitializeService(deployState, hostResource, container);
        admin.setLogserverContainerCluster(logServerCluster);
        context.getConfigModelRepoAdder().add(logserverClusterModel);
    }

    private Collection<HostResource> allocateHosts(HostSystem hostSystem, String clusterId, NodesSpecification nodesSpecification) {
        return nodesSpecification.provision(hostSystem, 
                                            ClusterSpec.Type.admin, 
                                            ClusterSpec.Id.from(clusterId), 
                                            context.getDeployLogger(),
                                            false,
                                            context.clusterInfo().build())
                                 .keySet();
    }

    /**
     * Returns a list of container hosts to use for an auxiliary cluster.
     * The list returns the same nodes on each invocation given the same available nodes.
     *
     * @param count the desired number of nodes. More nodes may be returned to ensure a smooth transition
     *        on topology changes, and less nodes may be returned if fewer are available
     * @param minHostsPerContainerCluster the desired number of hosts per cluster
     */
    private List<HostResource> pickContainerHostsForSlobrok(int count, int minHostsPerContainerCluster) {
        int hostsPerCluster = (int) Math.max(minHostsPerContainerCluster,
                                             Math.ceil((double) count / containerModels.size()));

        // Pick from all container clusters to make sure we don't lose all nodes at once if some clusters are removed.
        // This will overshoot the desired size (due to ceil and picking at least one node per cluster).
        List<HostResource> picked = new ArrayList<>();
        for (ContainerModel containerModel : containerModels)
            picked.addAll(pickContainerHostsFrom(containerModel, hostsPerCluster));
        return picked;
    }

    private List<HostResource> pickContainerHostsFrom(ContainerModel model, int count) {
        boolean retired = true;
        List<HostResource> picked = sortedContainerHostsFrom(model, count, !retired);

        // if we can return multiple hosts, include retired nodes which would have been picked before
        // (probably - assuming all previous nodes were retired, which is always true for a single cluster,
        // to ensure a smoother transition between the old and new topology
        // by including both new and old nodes during the retirement period
        picked.addAll(sortedContainerHostsFrom(model, count, retired));

        return picked;
    }

    /** Returns the count first containers in the current model having isRetired set to the given value */
    private List<HostResource> sortedContainerHostsFrom(ContainerModel model, int count, boolean retired) {
        List<HostResource> hosts = model.getCluster().getContainers().stream()
                                                                     .filter(container -> retired == container.isRetired())
                                                                     .map(Container::getHostResource)
                                                                     .sorted(HostResource::comparePrimarilyByIndexTo)
                                                                     .collect(Collectors.toCollection(ArrayList::new));
        return hosts.subList(0, Math.min(count, hosts.size()));
    }

    private Logserver createLogserver(DeployState deployState, Admin admin, Collection<HostResource> hosts) {
        Logserver logserver = new Logserver(admin);
        logserver.setHostResource(hosts.iterator().next());
        admin.setLogserver(logserver);
        logserver.initService(deployState);
        return logserver;
    }

    private void createSlobroks(DeployState deployState, Admin admin, Collection<HostResource> hosts) {
        if (hosts.isEmpty()) return; // No slobroks can be created (and none are needed)
        List<Slobrok> slobroks = new ArrayList<>();
        int index = 0;
        for (HostResource host : hosts) {
            Slobrok slobrok = new Slobrok(admin, index++, deployState.featureFlags());
            slobrok.setHostResource(host);
            slobroks.add(slobrok);
            slobrok.initService(deployState);
        }
        admin.addSlobroks(slobroks);
    }

}
