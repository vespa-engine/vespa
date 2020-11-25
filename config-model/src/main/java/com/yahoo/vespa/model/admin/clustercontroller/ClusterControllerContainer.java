// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.api.Reindexing;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.core.documentapi.DocumentAccessProvider;
import com.yahoo.container.di.config.PlatformBundlesConfig;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.reindexing.ReindexingConfig;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.xml.PlatformBundles;

import java.util.Set;
import java.util.TreeSet;

/**
 * Container implementation for cluster-controllers
 */
@RestartConfigs({FleetcontrollerConfig.class, ZookeeperServerConfig.class})
public class ClusterControllerContainer extends Container implements
        PlatformBundlesConfig.Producer,
        ZookeeperServerConfig.Producer,
        ReindexingConfig.Producer
{
    private static final ComponentSpecification CLUSTERCONTROLLER_BUNDLE = new ComponentSpecification("clustercontroller-apps");
    private static final ComponentSpecification ZOOKEEPER_SERVER_BUNDLE = new ComponentSpecification("zookeeper-server");
    private static final ComponentSpecification REINDEXING_CONTROLLER_BUNDLE = new ComponentSpecification("clustercontroller-reindexer");

    private final Set<String> bundles = new TreeSet<>();
    private final ReindexingContext reindexingContext;

    public ClusterControllerContainer(
            AbstractConfigProducer<?> parent,
            int index,
            boolean runStandaloneZooKeeper,
            boolean isHosted,
            ReindexingContext reindexingContext) {
        super(parent, "" + index, index, isHosted);
        this.reindexingContext = reindexingContext;

        addHandler("clustercontroller-status",
                   "com.yahoo.vespa.clustercontroller.apps.clustercontroller.StatusHandler",
                   "/clustercontroller-status/*",
                   CLUSTERCONTROLLER_BUNDLE);
        addHandler("clustercontroller-state-restapi-v2",
                   "com.yahoo.vespa.clustercontroller.apps.clustercontroller.StateRestApiV2Handler",
                   "/cluster/v2/*",
                   CLUSTERCONTROLLER_BUNDLE);
        if (runStandaloneZooKeeper) {
            addComponent("clustercontroller-zkrunner",
                         "com.yahoo.vespa.zookeeper.VespaZooKeeperServerImpl",
                         ZOOKEEPER_SERVER_BUNDLE);
            addComponent("clustercontroller-zkprovider",
                         "com.yahoo.vespa.clustercontroller.apps.clustercontroller.StandaloneZooKeeperProvider",
                         CLUSTERCONTROLLER_BUNDLE);
        } else {
            // TODO bjorncs/jonmv: remove extraneous ZooKeeperProvider layer
            addComponent(
                    "clustercontroller-zkrunner",
                    "com.yahoo.vespa.zookeeper.DummyVespaZooKeeperServer",
                    ZOOKEEPER_SERVER_BUNDLE);
            addComponent("clustercontroller-zkprovider",
                         "com.yahoo.vespa.clustercontroller.apps.clustercontroller.DummyZooKeeperProvider",
                         CLUSTERCONTROLLER_BUNDLE);
        }
        addComponent(new AccessLogComponent(AccessLogComponent.AccessLogType.jsonAccessLog, "controller", isHosted));

        // TODO: Why are bundles added here instead of in the cluster?
        addFileBundle("clustercontroller-apps");
        addFileBundle("clustercontroller-apputil");
        addFileBundle("clustercontroller-core");
        addFileBundle("clustercontroller-utils");
        addFileBundle("zookeeper-server");
        configureReindexing();
    }

    @Override
    public int getWantedPort() {
        return 19050;
    }

    @Override
    public boolean requiresWantedPort() {
        return index() == 0;
    }

    @Override
    public ContainerServiceType myServiceType() {
        return ContainerServiceType.CLUSTERCONTROLLER_CONTAINER;
    }

    private void addHandler(Handler h, String path) {
        h.addServerBindings(SystemBindingPattern.fromHttpPath(path));
        super.addHandler(h);
    }

    private void addFileBundle(String bundleName) {
        bundles.add(PlatformBundles.absoluteBundlePath(bundleName).toString());
    }

    private ComponentModel createComponentModel(String id, String className, ComponentSpecification bundle) {
        return new ComponentModel(new BundleInstantiationSpecification(new ComponentSpecification(id),
                                                                       new ComponentSpecification(className),
                                                                       bundle));
    }

    private void addComponent(String id, String className, ComponentSpecification bundle) {
        addComponent(new Component<>(createComponentModel(id, className, bundle)));
    }

    private void addHandler(String id, String className, String path, ComponentSpecification bundle) {
        addHandler(new Handler(createComponentModel(id, className, bundle)), path);
    }

    private void configureReindexing() {
        if (reindexingContext != null) {
            addFileBundle(REINDEXING_CONTROLLER_BUNDLE.getName());
            addComponent(new SimpleComponent(DocumentAccessProvider.class.getName()));
            addComponent("reindexing-maintainer",
                         "ai.vespa.reindexing.ReindexingMaintainer",
                         REINDEXING_CONTROLLER_BUNDLE);
            addHandler("reindexing-status",
                       "ai.vespa.reindexing.http.ReindexingV1ApiHandler",
                       "/reindexing/v1/*",
                       REINDEXING_CONTROLLER_BUNDLE);
        }
    }


    @Override
    public void getConfig(PlatformBundlesConfig.Builder builder) {
        bundles.forEach(builder::bundlePaths);
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        builder.myid(index());
    }

    @Override
    public void getConfig(ReindexingConfig.Builder builder) {
        if (reindexingContext == null)
            return;

        builder.clusterName(reindexingContext.contentClusterName());
        builder.enabled(reindexingContext.reindexing().enabled());
        for (NewDocumentType type : reindexingContext.documentTypes()) {
            String typeName = type.getFullName().getName();
            reindexingContext.reindexing().status(reindexingContext.contentClusterName(), typeName)
                             .ifPresent(status -> builder.status(typeName,
                                                                 new ReindexingConfig.Status.Builder()
                                                                         .readyAtMillis(status.ready().toEpochMilli())));
        }
    }

}
