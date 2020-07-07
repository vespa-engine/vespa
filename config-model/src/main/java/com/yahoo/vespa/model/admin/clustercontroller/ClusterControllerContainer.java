// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.BundlesConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.di.PlatformBundlesConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;

import java.util.Set;
import java.util.TreeSet;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * Container implementation for cluster-controllers
 */
@RestartConfigs({FleetcontrollerConfig.class, ZookeeperServerConfig.class})
public class ClusterControllerContainer extends Container implements
        BundlesConfig.Producer,
        PlatformBundlesConfig.Producer,
        ZookeeperServerConfig.Producer
{
    private static final ComponentSpecification CLUSTERCONTROLLER_BUNDLE = new ComponentSpecification("clustercontroller-apps");
    private static final ComponentSpecification ZOOKEEPER_SERVER_BUNDLE = new ComponentSpecification("zookeeper-server");

    private final Set<String> bundles = new TreeSet<>();

    public ClusterControllerContainer(AbstractConfigProducer parent, int index, boolean runStandaloneZooKeeper, boolean isHosted) {
        super(parent, "" + index, index);
        addHandler("clustercontroller-status",
                   "com.yahoo.vespa.clustercontroller.apps.clustercontroller.StatusHandler",
                   "clustercontroller-status/*");
        addHandler("clustercontroller-state-restapi-v2",
                   "com.yahoo.vespa.clustercontroller.apps.clustercontroller.StateRestApiV2Handler",
                   "cluster/v2/*");
        if (runStandaloneZooKeeper) {
            addComponent("clustercontroller-zkrunner",
                         "com.yahoo.vespa.zookeeper.VespaZooKeeperServerImpl",
                         ZOOKEEPER_SERVER_BUNDLE);
            addComponent("clustercontroller-zkprovider",
                         "com.yahoo.vespa.clustercontroller.apps.clustercontroller.StandaloneZooKeeperProvider",
                         CLUSTERCONTROLLER_BUNDLE);
        } else {
            addComponent("clustercontroller-zkprovider",
                         "com.yahoo.vespa.clustercontroller.apps.clustercontroller.DummyZooKeeperProvider",
                         CLUSTERCONTROLLER_BUNDLE);
        }
        addComponent(new AccessLogComponent(AccessLogComponent.AccessLogType.jsonAccessLog, "controller", isHosted));

        addFileBundle("lib/jars/clustercontroller-apps-jar-with-dependencies.jar");
        addFileBundle("lib/jars/clustercontroller-apputil-jar-with-dependencies.jar");
        addFileBundle("lib/jars/clustercontroller-core-jar-with-dependencies.jar");
        addFileBundle("lib/jars/clustercontroller-utils-jar-with-dependencies.jar");
        addFileBundle("lib/jars/zookeeper-server-jar-with-dependencies.jar");
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

    private void addHandler(Handler h, String binding) {
        h.addServerBindings("http://*/" + binding);
        super.addHandler(h);
    }

    private void addFileBundle(String bundlePath) {
        bundles.add("file:" + getDefaults().underVespaHome(bundlePath));
    }

    private ComponentModel createComponentModel(String id, String className, ComponentSpecification bundle) {
        return new ComponentModel(new BundleInstantiationSpecification(new ComponentSpecification(id),
                                                                       new ComponentSpecification(className),
                                                                       bundle));
    }

    private void addComponent(String id, String className, ComponentSpecification bundle) {
        addComponent(new Component<>(createComponentModel(id, className, bundle)));
    }

    private void addHandler(String id, String className, String binding) {
        addHandler(new Handler(createComponentModel(id, className, CLUSTERCONTROLLER_BUNDLE)),
                   binding);
    }

    @Override
    public void getConfig(BundlesConfig.Builder builder) {
        for (String bundle : bundles) {
            builder.bundle(bundle);
        }
    }

    @Override
    public void getConfig(PlatformBundlesConfig.Builder builder) {
        bundles.forEach(builder::bundles);
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        builder.myid(index());
    }

}
