// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.SystemName;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.Logserver;
import com.yahoo.vespa.model.admin.Slobrok;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.component.Handler;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Builds the admin model from a version 4 XML tag, or as a default when an admin 3 tag or no admin tag is used.
 *
 * @author bratseth
 */
public class DomAdminV4Builder extends DomAdminBuilderBase {

    private ApplicationId ZONE_APPLICATION_ID = ApplicationId.from("hosted-vespa", "routing", "default");

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
    protected void doBuildAdmin(DeployState deployState, Admin admin, Element w3cAdminElement) {
        ModelElement adminElement = new ModelElement(w3cAdminElement);
        admin.addConfigservers(getConfigServersFromSpec(deployState.getDeployLogger(), admin));

        // Note: These two elements only exists in admin version 4.0
        // This build handles admin version 3.0 by ignoring its content (as the content is not useful)
        Optional<NodesSpecification> requestedSlobroks = 
                NodesSpecification.optionalDedicatedFromParent(adminElement.getChild("slobroks"), context);
        Optional<NodesSpecification> requestedLogservers = 
                NodesSpecification.optionalDedicatedFromParent(adminElement.getChild("logservers"), context);

        assignSlobroks(deployState.getDeployLogger(), requestedSlobroks.orElse(NodesSpecification.nonDedicated(3, context)), admin);
        assignLogserver(deployState, requestedLogservers.orElse(createNodesSpecificationForLogserver()), admin);

        addLogForwarders(adminElement.getChild("logforwarding"), admin);
    }

    private void assignSlobroks(DeployLogger deployLogger, NodesSpecification nodesSpecification, Admin admin) {
        if (nodesSpecification.isDedicated()) {
            createSlobroks(deployLogger, admin, allocateHosts(admin.getHostSystem(), "slobroks", nodesSpecification));
        }
        else {
            createSlobroks(deployLogger, admin, pickContainerHostsForSlobrok(nodesSpecification.count(), 2));
        }
    }

    private void assignLogserver(DeployState deployState, NodesSpecification nodesSpecification, Admin admin) {
        if (nodesSpecification.count() > 1) throw new IllegalArgumentException("You can only request a single log server");

        if (nodesSpecification.isDedicated()) {
            Collection<HostResource> hosts = allocateHosts(admin.getHostSystem(), "logserver", nodesSpecification);
            if (hosts.isEmpty()) return; // No log server can be created (and none is needed)

            Logserver logserver = createLogserver(deployState.getDeployLogger(), admin, hosts);
            createAdditionalContainerOnLogserverHost(deployState, admin, logserver.getHostResource());
        } else if (containerModels.iterator().hasNext()) {
            List<HostResource> hosts = sortedContainerHostsFrom(containerModels.iterator().next(), nodesSpecification.count(), false);
            if (hosts.isEmpty()) return; // No log server can be created (and none is needed)

            createLogserver(deployState.getDeployLogger(), admin, hosts);
        } else {
            context.getDeployLogger().log(LogLevel.INFO, "No container host available to use for running logserver");
        }
    }

    private NodesSpecification createNodesSpecificationForLogserver() {
        // TODO: Enable for main system as well
        DeployState deployState = context.getDeployState();
        if (deployState.getProperties().useDedicatedNodeForLogserver() &&
                context.getApplicationType() == ConfigModelContext.ApplicationType.DEFAULT &&
                deployState.isHosted() &&
                logServerFlagValue(deployState))
            return NodesSpecification.dedicated(1, context);
        else
            return NodesSpecification.nonDedicated(1, context);
    }

    // Creates a container cluster 'logserver-cluster' with 1 container on logserver host
    // for setting up a handler for getting logs from logserver
    private void createAdditionalContainerOnLogserverHost(DeployState deployState, Admin admin, HostResource hostResource) {
        ContainerCluster logServerCluster = new ContainerCluster(admin, "logserver-cluster", "logserver-cluster", deployState);
        ContainerModel logserverClusterModel = new ContainerModel(context.withParent(admin).withId(logServerCluster.getSubId()));

        // Add base handlers and the log handler
        logServerCluster.addMetricStateHandler();
        logServerCluster.addApplicationStatusHandler();
        logServerCluster.addDefaultRootHandler();
        logServerCluster.addVipHandler();
        addLogHandler(logServerCluster);

        logserverClusterModel.setCluster(logServerCluster);

        Container container = new Container(logServerCluster, "" + 0, 0, deployState.isHosted());
        container.setHostResource(hostResource);
        container.initService(deployState.getDeployLogger());
        logServerCluster.addContainer(container);
        admin.addAndInitializeService(deployState.getDeployLogger(), hostResource, container);
        admin.setLogserverContainerCluster(logServerCluster);
    }

    private void addLogHandler(ContainerCluster cluster) {
        Handler<?> logHandler = Handler.fromClassName("com.yahoo.container.handler.LogHandler");
        logHandler.addServerBindings("http://*/logs", "https://*/logs");
        cluster.addComponent(logHandler);
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
    private List<HostResource> pickContainerHostsForSlobrok(int count, int minHostsPerContainerCluster) {
        Collection<ContainerModel> containerModelsWithSlobrok = containerModels.stream()
                .filter(this::shouldHaveSlobrok)
                .collect(Collectors.toList());
        int hostsPerCluster = (int) Math.max(minHostsPerContainerCluster,
                                             Math.ceil((double) count / containerModelsWithSlobrok.size()));

        // Pick from all container clusters to make sure we don't lose all nodes at once if some clusters are removed.
        // This will overshoot the desired size (due to ceil and picking at least one node per cluster).
        List<HostResource> picked = new ArrayList<>();
        for (ContainerModel containerModel : containerModelsWithSlobrok)
            picked.addAll(pickContainerHostsFrom(containerModel, hostsPerCluster));
        return picked;
    }

    private boolean shouldHaveSlobrok(ContainerModel containerModel) {
        // Avoid Slobroks on node-admin container cluster, as node-admin is migrating
        // TODO: Remove this hack once node-admin has migrated out the zone app

        ApplicationId applicationId = context.getDeployState().getProperties().applicationId();
        if (!applicationId.equals(ZONE_APPLICATION_ID)) {
            return true;
        }

        // aka clustername, aka application-model's ClusterId
        String clustername = containerModel.getCluster().getName();
        return !Objects.equals(clustername, "node-admin");
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

    private Logserver createLogserver(DeployLogger deployLogger, Admin admin, Collection<HostResource> hosts) {
        Logserver logserver = new Logserver(admin);
        logserver.setHostResource(hosts.iterator().next());
        admin.setLogserver(logserver);
        logserver.initService(deployLogger);
        return logserver;
    }

    private void createSlobroks(DeployLogger deployLogger, Admin admin, Collection<HostResource> hosts) {
        if (hosts.isEmpty()) return; // No slobroks can be created (and none are needed)
        List<Slobrok> slobroks = new ArrayList<>();
        int index = 0;
        for (HostResource host : hosts) {
            Slobrok slobrok = new Slobrok(admin, index++);
            slobrok.setHostResource(host);
            slobroks.add(slobrok);
            slobrok.initService(deployLogger);
        }
        admin.addSlobroks(slobroks);
    }

    private boolean logServerFlagValue(DeployState deployState) {
        return Flags.ENABLE_LOGSERVER.bindTo(deployState.flagSource())
                .with(FetchVector.Dimension.APPLICATION_ID, deployState.getProperties().applicationId().serializedForm())
                .value();
    }

}
