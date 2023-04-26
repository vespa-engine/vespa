// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.Configserver;
import com.yahoo.vespa.model.admin.Logserver;
import com.yahoo.vespa.model.admin.Slobrok;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerCluster;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainerCluster;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder.DomConfigProducerBuilderBase;
import com.yahoo.vespa.model.container.Container;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Builds the admin model from a V2 admin XML tag.
 *
 * @author vegardh
 */
public class DomAdminV2Builder extends DomAdminBuilderBase {

    private static final String ATTRIBUTE_CLUSTER_CONTROLLER_STANDALONE_ZK = "standalone-zookeeper";

    public DomAdminV2Builder(ConfigModelContext.ApplicationType applicationType,
                             boolean multitenant,
                             List<ConfigServerSpec> configServerSpecs) {
        super(applicationType, multitenant, configServerSpecs);
    }

    @Override
    protected void doBuildAdmin(DeployState deployState, Admin admin, Element adminE) {
        List<Configserver> configservers = parseConfigservers(deployState, admin, adminE);
        admin.setLogserver(parseLogserver(deployState, admin, adminE));
        admin.addConfigservers(configservers);
        admin.addSlobroks(getSlobroks(deployState, admin, XML.getChild(adminE, "slobroks")));
        if ( ! admin.multitenant())
            admin.setClusterControllers(addConfiguredClusterControllers(deployState, admin, adminE), deployState);

        addLogForwarders(new ModelElement(adminE).child("logforwarding"), admin, deployState);
        addLoggingSpecs(new ModelElement(adminE).child("logging"), admin);
    }

    private List<Configserver> parseConfigservers(DeployState deployState, Admin admin, Element adminE) {
        List<Configserver> configservers;
        if (multitenant)
            configservers = getConfigServersFromSpec(deployState, admin);
        else
            configservers = getConfigServers(deployState, admin, adminE);
        if (configservers.isEmpty() && ! multitenant)
            configservers = createSingleConfigServer(deployState, admin);
        if (configservers.size() % 2 == 0)
            deployState.getDeployLogger().logApplicationPackage(Level.WARNING,
                                                                "An even number (" + configservers.size() +
                                                                ") of config servers have been configured. " +
                                                                "This is discouraged, see doc for configuration server ");
        return configservers;
    }

    private Logserver parseLogserver(DeployState deployState, Admin admin, Element adminE) {
        Element logserverE = XML.getChild(adminE, "logserver");
        if (logserverE == null) {
            var adminserverE = XML.getChild(adminE, "adminserver");
            logserverE = adminserverE != null ? adminserverE : adminE;
        }
        return new LogserverBuilder().build(deployState, admin, logserverE);
    }

    private ClusterControllerContainerCluster addConfiguredClusterControllers(DeployState deployState,
                                                                              TreeConfigProducer<?> parent,
                                                                              Element admin) {
        Element controllersElements = XML.getChild(admin, "cluster-controllers");
        if (controllersElements == null) return null;

        List<Element> controllers = XML.getChildren(controllersElements, "cluster-controller");
        if (controllers.isEmpty()) return null;

        boolean standaloneZooKeeper = "true".equals(controllersElements.getAttribute(ATTRIBUTE_CLUSTER_CONTROLLER_STANDALONE_ZK)) || multitenant;
        if (standaloneZooKeeper) {
            parent = new ClusterControllerCluster(parent, "standalone", deployState);
        }
        var cluster = new ClusterControllerContainerCluster(parent,
                                                            "cluster-controllers",
                                                            "cluster-controllers",
                                                            deployState);

        List<ClusterControllerContainer> containers = new ArrayList<>();

        for (Element controller : controllers) {
            ClusterControllerContainer clusterController = new ClusterControllerBuilder(containers.size(), standaloneZooKeeper).build(deployState, cluster, controller);
            containers.add(clusterController);
        }

        cluster.addContainers(containers);
        return cluster;
    }

    private List<Configserver> getConfigServers(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element adminE) {
        Element configserversE = XML.getChild(adminE, "configservers");
        if (configserversE == null) {
            Element adminserver = XML.getChild(adminE, "adminserver");
            if (adminserver == null) {
                return createSingleConfigServer(deployState, parent);
            } else {
                SimpleConfigProducer<AnyConfigProducer> configServers = new SimpleConfigProducer<>(parent, "configservers");
                return List.of(new ConfigserverBuilder(0, configServerSpecs).build(deployState, configServers, adminserver));
            }
        }
        else {
            SimpleConfigProducer<AnyConfigProducer> configServers = new SimpleConfigProducer<>(parent, "configservers");
            List<Configserver> configservers = new ArrayList<>();
            int i = 0;
            for (Element configserverE : XML.getChildren(configserversE, "configserver"))
                configservers.add(new ConfigserverBuilder(i++, configServerSpecs).build(deployState, configServers, configserverE));
            return configservers;
        }
    }

    /** Fallback when no config server is specified */
    private List<Configserver> createSingleConfigServer(DeployState deployState, TreeConfigProducer<?> parent) {
        SimpleConfigProducer<AnyConfigProducer> configServers = new SimpleConfigProducer<>(parent, "configservers");
        Configserver configServer = new Configserver(configServers, "configserver", Configserver.defaultRpcPort);
        configServer.setHostResource(parent.hostSystem().getHost(Container.SINGLENODE_CONTAINER_SERVICESPEC));
        configServer.initService(deployState);
        return List.of(configServer);
    }

    private List<Slobrok> getSlobroks(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element slobroksE) {
        List<Slobrok> slobroks = new ArrayList<>();
        if (slobroksE != null)
            slobroks = getExplicitSlobrokSetup(deployState, parent, slobroksE);
        return slobroks;
    }

    private List<Slobrok> getExplicitSlobrokSetup(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element slobroksE) {
        List<Slobrok> slobroks = new ArrayList<>();
        int i = 0;
        for (Element e : XML.getChildren(slobroksE, "slobrok"))
            slobroks.add(new SlobrokBuilder(i++).build(deployState, parent, e));
        return slobroks;
    }

    private static class LogserverBuilder extends DomConfigProducerBuilderBase<Logserver> {
        public LogserverBuilder() {
        }

        @Override
        protected Logserver doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element producerSpec) {
            return new Logserver(parent);
        }
    }

    private static class ConfigserverBuilder extends DomConfigProducerBuilderBase<Configserver> {
        private final int i;
        private final int rpcPort;

        public ConfigserverBuilder(int i, List<ConfigServerSpec> configServerSpec) {
            this.i = i;
            Objects.requireNonNull(configServerSpec);
            if (configServerSpec.size() > 0)
                this.rpcPort = configServerSpec.get(0).getConfigServerPort();
            else
                this.rpcPort = Configserver.defaultRpcPort;
        }

        @Override
        protected Configserver doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element spec) {
            var configServer = new Configserver(parent, "configserver." + i, rpcPort);
            configServer.setProp("index", i);
            return configServer;
        }
    }

    private static class SlobrokBuilder extends DomConfigProducerBuilderBase<Slobrok> {

        int i;

        public SlobrokBuilder(int i) {
            this.i = i;
        }

        @Override
        protected Slobrok doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element spec) {
            return new Slobrok(parent, i, deployState.featureFlags());
        }

    }

    private static class ClusterControllerBuilder extends DomConfigProducerBuilderBase<ClusterControllerContainer> {
        int i;
        boolean runStandaloneZooKeeper;

        public ClusterControllerBuilder(int i, boolean runStandaloneZooKeeper) {
            this.i = i;
            this.runStandaloneZooKeeper = runStandaloneZooKeeper;
        }

        @Override
        protected ClusterControllerContainer doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> parent, Element spec) {
            return new ClusterControllerContainer(parent, i, runStandaloneZooKeeper, deployState, false);
        }
    }

}
