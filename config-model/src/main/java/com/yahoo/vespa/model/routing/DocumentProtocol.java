// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.routing;

import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.document.select.DocumentSelector;
import com.yahoo.documentapi.messagebus.protocol.DocumentrouteselectorpolicyConfig;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocolPoliciesConfig;
import com.yahoo.messagebus.routing.ApplicationSpec;
import com.yahoo.messagebus.routing.HopSpec;
import com.yahoo.messagebus.routing.RouteSpec;
import com.yahoo.messagebus.routing.RoutingTableSpec;
import com.yahoo.vespa.config.content.MessagetyperouteselectorpolicyConfig;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * This class is responsible for generating all hops and routes for the Document protocol running on message bus. All
 * the code within could really be part of {@link Routing}, but it has been partitioned out to allow better readability
 * and also more easily maintainable as the number of protocols increase.
 *
 * @author Simon Thoresen Hult
 */
public final class DocumentProtocol implements Protocol,
                                               DocumentrouteselectorpolicyConfig.Producer,
                                               DocumentProtocolPoliciesConfig.Producer {

    private static final String NAME = "document";
    private final ApplicationSpec application;
    private final RoutingTableSpec routingTable;
    private final ConfigModelRepo repo;

    public static String getIndexedRouteName(String configId) {
        return configId + "-index";
    }

    public static String getDirectRouteName(String configId) {
        return configId + "-direct";
    }

    /**
     * Constructs a new document protocol based on the content of the given plugins.
     *
     * @param plugins the plugins to reflect on
     */
    DocumentProtocol(ConfigModelRepo plugins) {
        application = createApplicationSpec(plugins);
        routingTable = createRoutingTable(plugins);
        this.repo = plugins;
    }

    /**
     * Creates a service index based on the plugins loaded. This means to fill the index with all services known by this
     * protocol by traversing the plugins.
     *
     * @param plugins All initialized plugins of the Vespa model
     * @return the index of all known services
     */
    private static ApplicationSpec createApplicationSpec(ConfigModelRepo plugins) {
        ApplicationSpec ret = new ApplicationSpec();

        for (ContentCluster cluster : Content.getContentClusters(plugins)) {
            for (com.yahoo.vespa.model.content.Distributor node : cluster.getDistributorNodes().getChildren().values()) {
                ret.addService(NAME, node.getConfigId() + "/default");
            }
        }

        for (ContainerCluster<?> containerCluster: ContainerModel.containerClusters(plugins)) {
            ContainerDocproc containerDocproc = containerCluster.getDocproc();
            if (containerDocproc != null) {
                createDocprocChainSpec(ret,
                                       containerDocproc.getChains().allChains().allComponents(),
                                       containerCluster.getContainers());
            }
        }

        return ret;
    }

    private static void createDocprocChainSpec(ApplicationSpec spec,
                                               List<DocprocChain> docprocChains,
                                               List<? extends Container> containerNodes) {
        for (DocprocChain chain: docprocChains) {
            for (Container node: containerNodes)
                spec.addService(NAME, node.getConfigId() + "/chain." + chain.getComponentId().stringValue());
        }
    }

    @Override
    public void getConfig(DocumentrouteselectorpolicyConfig.Builder builder) {
        for (ContentCluster cluster : Content.getContentClusters(repo)) {
            addRoute(cluster.getConfigId(), cluster.getRoutingSelector(), builder);
        }
    }

    @Override
    public void getConfig(DocumentProtocolPoliciesConfig.Builder builder) {
        for (ContentCluster cluster : Content.getContentClusters(repo)) {
            DocumentProtocolPoliciesConfig.Cluster.Builder clusterBuilder = new DocumentProtocolPoliciesConfig.Cluster.Builder();
            addSelector(cluster.getConfigId(), cluster.getRoutingSelector(), clusterBuilder);
            if (cluster.getSearch().hasIndexedCluster())
                addRoutes(getDirectRouteName(cluster.getConfigId()), getIndexedRouteName(cluster.getConfigId()), clusterBuilder);
            else
                clusterBuilder.defaultRoute(cluster.getConfigId());

            builder.cluster(cluster.getConfigId(), clusterBuilder);
        }
    }

    public static void getConfig(MessagetyperouteselectorpolicyConfig.Builder builder, String configId) {
        builder.defaultroute(getDirectRouteName(configId))
                .route(new MessagetyperouteselectorpolicyConfig.Route.Builder()
                        .messagetype(com.yahoo.documentapi.messagebus.protocol.DocumentProtocol.MESSAGE_PUTDOCUMENT)
                        .name(getIndexedRouteName(configId)))
                .route(new MessagetyperouteselectorpolicyConfig.Route.Builder()
                        .messagetype(com.yahoo.documentapi.messagebus.protocol.DocumentProtocol.MESSAGE_REMOVEDOCUMENT)
                        .name(getIndexedRouteName(configId)))
                .route(new MessagetyperouteselectorpolicyConfig.Route.Builder()
                        .messagetype(com.yahoo.documentapi.messagebus.protocol.DocumentProtocol.MESSAGE_UPDATEDOCUMENT)
                        .name(getIndexedRouteName(configId)));
    }

    private static void addRoutes(String directRoute, String indexedRoute, DocumentProtocolPoliciesConfig.Cluster.Builder builder) {
            builder.defaultRoute(directRoute)
                   .route(new DocumentProtocolPoliciesConfig.Cluster.Route.Builder()
                                  .messageType(com.yahoo.documentapi.messagebus.protocol.DocumentProtocol.MESSAGE_PUTDOCUMENT)
                                  .name(indexedRoute))
                   .route(new DocumentProtocolPoliciesConfig.Cluster.Route.Builder()
                                  .messageType(com.yahoo.documentapi.messagebus.protocol.DocumentProtocol.MESSAGE_REMOVEDOCUMENT)
                                  .name(indexedRoute))
                   .route(new DocumentProtocolPoliciesConfig.Cluster.Route.Builder()
                                  .messageType(com.yahoo.documentapi.messagebus.protocol.DocumentProtocol.MESSAGE_UPDATEDOCUMENT)
                                  .name(indexedRoute));
    }

    private static void addSelector(String clusterConfigId, String selector, DocumentProtocolPoliciesConfig.Cluster.Builder builder) {
        try {
            new DocumentSelector(selector);
        } catch (com.yahoo.document.select.parser.ParseException e) {
            throw new IllegalArgumentException("Failed to parse selector '" + selector +
                                               "' for route '" + clusterConfigId +
                                               "' in policy 'DocumentRouteSelector'.");
        }
        builder.selector(selector);
    }

    private static void addRoute(String clusterConfigId, String selector, DocumentrouteselectorpolicyConfig.Builder builder) {
        try {
            new DocumentSelector(selector);
        } catch (com.yahoo.document.select.parser.ParseException e) {
            throw new IllegalArgumentException("Failed to parse selector '" + selector +
                                               "' for route '" + clusterConfigId +
                                               "' in policy 'DocumentRouteSelector'.");
        }
        DocumentrouteselectorpolicyConfig.Route.Builder routeBuilder = new DocumentrouteselectorpolicyConfig.Route.Builder();
        routeBuilder.name(clusterConfigId);
        routeBuilder.selector(selector);
        builder.route(routeBuilder);
    }

    /**
     * This function extrapolates any routes for the document protocol that it can from the vespa model.
     *
     * @param plugins all initialized plugins of the vespa model
     * @return routing table for the document protocol
     */
    private static RoutingTableSpec createRoutingTable(ConfigModelRepo plugins) {
        // Build simple hops and routes.
        List<ContentCluster> content = Content.getContentClusters(plugins);
        Collection<ContainerCluster<?>> containerClusters = ContainerModel.containerClusters(plugins);

        RoutingTableSpec table = new RoutingTableSpec(NAME);
        addContainerClusterDocprocHops(containerClusters, table);
        addContentRouting(content, table);

        // Build the indexing hop if it is possible to derive.
        addIndexingHop(content, table);

        // Build the default routes if possible
        addDefaultRoutes(content, containerClusters, table);

        // Return the complete routing table.
        simplifyRouteNames(table);
        return table;
    }

    private static void addContainerClusterDocprocHops(Collection<ContainerCluster<?>> containerClusters, RoutingTableSpec table) {
        for (ContainerCluster<?> cluster: containerClusters) {
            ContainerDocproc docproc = cluster.getDocproc();

            if (docproc != null) {
                for (DocprocChain chain : docproc.getChains().allChains().allComponents()) {
                    addChainHop(table, cluster.getConfigId(), chain);
                }
            }
        }
    }

    private static void addChainHop(RoutingTableSpec table, String configId, DocprocChain chain) {
        StringBuilder selector = new StringBuilder();
        selector.append("[LoadBalancer:cluster=").append(configId)
                .append(";session=").append(chain.getSessionName())
                .append("]");
        table.addHop(new HopSpec(chain.getServiceName(), selector.toString()));
    }

    /**
     * Create hops to all configured storage nodes for the Document protocol. The "Distributor" policy resolves its
     * recipients using slobrok lookups, so it requires no configured recipients.
     *
     * @param content the storage model from {@link com.yahoo.vespa.model.VespaModel}
     * @param table   the routing table to add to
     */
    private static void addContentRouting(List<ContentCluster> content, RoutingTableSpec table) {
        for (ContentCluster cluster : content) {
            RouteSpec spec = new RouteSpec(cluster.getConfigId());

            if (cluster.getSearch().hasIndexedCluster()) {
                table.addRoute(spec.addHop("[MessageType:" + cluster.getConfigId() + "]"));
                table.addRoute(new RouteSpec(getIndexedRouteName(cluster.getConfigId()))
                                       .addHop(cluster.getSearch().getIndexed().getIndexingServiceName())
                                       .addHop("[Content:cluster=" + cluster.getName() + "]"));
                table.addRoute(new RouteSpec(getDirectRouteName(cluster.getConfigId()))
                                       .addHop("[Content:cluster=" + cluster.getName() + "]"));
            } else {
                table.addRoute(spec.addHop("[Content:cluster=" + cluster.getName() + "]"));
            }
            table.addRoute(new RouteSpec("storage/cluster." + cluster.getName())
                                   .addHop("route:" + cluster.getConfigId()));
        }
    }

    /**
     * Create the "indexing" hop. This hop contains all non-streaming search clusters as recipients, and the routing
     * policy "SearchCluster" will decide which cluster(s) are to receive every document passed through it based on a
     * document select string derived from services.xml.
     *
     * @param table the routing table to add to
     */
    private static void addIndexingHop(List<ContentCluster> content, RoutingTableSpec table) {
        if (content.isEmpty()) return;

        HopSpec hop = new HopSpec("indexing", "[DocumentRouteSelector]");
        for (ContentCluster cluster : content) {
            hop.addRecipient(cluster.getConfigId());
        }
        if (hop.hasRecipients()) {
            table.addHop(hop);
        }
    }

    /**
     * Create the {@code default} and {@code default-get} routes for the Document protocol. The {@code default}
     * route will be either a route to storage or a route to search. Since recovery from storage is supported,
     * storage takes precedence over search when deciding on the final target of the default route. If there
     * is an unambiguous docproc cluster in the application, the {@code default} route will pass through it.
     * The {@code default-get} route skips the docproc but is otherwise identical to the {@code default} route.
     *
     * @param content the content model from {@link com.yahoo.vespa.model.VespaModel}
     * @param containerClusters a collection of {@link com.yahoo.vespa.model.container.ContainerCluster}s
     * @param table the routing table to add to
     */
    private static void addDefaultRoutes(List<ContentCluster> content,
                                         Collection<ContainerCluster<?>> containerClusters,
                                         RoutingTableSpec table) {
        if (content.isEmpty() || !indexingHopExists(table)) return;

        RouteSpec route = new RouteSpec("default");
        String hop = getContainerClustersDocprocHop(containerClusters);
        if (hop != null) {
            route.addHop(hop);
        }
        route.addHop("indexing");
        table.addRoute(route);

        if (content.size() == 1) {
            table.addRoute(new RouteSpec("default-get").addHop("[Content:cluster=" + content.get(0).getConfigId() + "]"));
        } else {
            //TODO This should ideally skip indexing and go directly to correct cluster.
            // But will handle the single cluster for now.
            table.addRoute(new RouteSpec("default-get").addHop("indexing"));
        }
    }

    private static boolean indexingHopExists(RoutingTableSpec table) {
        for (int i = 0, len = table.getNumHops(); i < len; ++i) {
            if (table.getHop(i).getName().equals("indexing")) {
                return true;
            }
        }
        return false;
    }

    private static String getContainerClustersDocprocHop(Collection<ContainerCluster<?>> containerClusters) {
        DocprocChain result = null;

        for (ContainerCluster<?> containerCluster: containerClusters) {
            DocprocChain defaultChain = getDefaultChain(containerCluster.getDocproc());
            if (defaultChain != null) {
                if (result != null)
                    throw new IllegalArgumentException("Only a single default docproc chain is allowed across all container clusters");
                result = defaultChain;
            }
        }

        return result == null ? null: result.getServiceName();
    }

    private static DocprocChain getDefaultChain(ContainerDocproc docproc) {
        return docproc == null
                ? null
                : docproc.getChains().allChains().getComponent("default");
    }

    /**
     * Attempts to simplify all route names by removing prefixing plugin name and whatever comes before the dot (.) in
     * the second naming element. This can only be done to those routes that do not share primary name elements with
     * other routes (e.g. a search clusters with the same name as a storage cluster).
     *
     * @param table the routing table whose route names are to be simplified
     */
    private static void simplifyRouteNames(RoutingTableSpec table) {
        if (table == null || !table.hasRoutes()) {
            return;
        }

        // Pass 1: Determine which simplifications are in conflict.
        Map<String, Set<String>> simple = new TreeMap<>();
        List<String> broken = new ArrayList<>();
        for (int i = 0, len = table.getNumRoutes(); i < len; ++i) {
            String before = table.getRoute(i).getName();
            String after = simplifyRouteName(before);
            if (simple.containsKey(after)) {
                Set<String> l = simple.get(after);
                l.add(before);
                if (!(l.contains("content/" + after) && l.contains("storage/cluster." + after) && (l.size() == 2))) {
                    broken.add(after);
                }
            } else {
                Set<String> l = new HashSet<>();
                l.add(before);
                simple.put(after, l);
            }
        }

        // Pass 2: Simplify all non-conflicting route names by alias.
        Set<RouteSpec> alias = new HashSet<>();
        Set<String> unique = new HashSet<>();
        for (int i = 0; i < table.getNumRoutes(); ) {
            RouteSpec route = table.getRoute(i);
            String before = route.getName();
            String after = simplifyRouteName(before);
            if (!before.equals(after)) {
                if (!broken.contains(after)) {
                    if (route.getNumHops() == 1 && route.getHop(0).equals(route.getName())) {
                        alias.add(new RouteSpec(after).addHop(route.getHop(0))); // full route name is redundant
                        unique.add(after);
                        table.removeRoute(i);
                        continue; // do not increment i
                    } else {
                        if (!unique.contains(after)) {
                            alias.add(new RouteSpec(after).addHop("route:" + before));
                            unique.add(after);
                        }
                    }
                }
            }
            ++i;
        }
        for (RouteSpec rs : alias) {
            table.addRoute(rs);
        }
    }

    /**
     * Returns a simplified version of the given route name. This method will remove the first component of the name as
     * separated by a forward slash, and then remove the first component of the remaining name as separated by a dot.
     *
     * @param name the route name to simplify
     * @return the simplified route name
     */
    private static String simplifyRouteName(String name) {
        String[] foo = name.split("/", 2);
        if (foo.length < 2) {
            return name;
        }
        String[] bar = foo[1].split("\\.", 2);
        if (bar.length < 2) {
            return foo[1];
        }
        return bar[1];
    }

    @Override
    public ApplicationSpec getApplicationSpec() {
        return application;
    }

    @Override
    public RoutingTableSpec getRoutingTableSpec() {
        return routingTable;
    }

}
