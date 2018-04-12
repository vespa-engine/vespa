// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.clustercontroller;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.BundlesConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.log.LogLevel;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;

import java.util.Set;
import java.util.TreeSet;

/**
 * Extends the container producer to allow us to override ports.
 */
@RestartConfigs({FleetcontrollerConfig.class, ZookeeperServerConfig.class})
public class ClusterControllerContainer extends Container implements
        BundlesConfig.Producer,
        ZookeeperServerConfig.Producer,
        QrStartConfig.Producer
{
    private static final ComponentSpecification CLUSTERCONTROLLER_BUNDLE = new ComponentSpecification("clustercontroller-apps");
    private static final ComponentSpecification ZKFACADE_BUNDLE = new ComponentSpecification("zkfacade");
    private final int index;

    private final Set<String> bundles = new TreeSet<>();

    public ClusterControllerContainer(AbstractConfigProducer parent, int index, boolean runStandaloneZooKeeper) {
        super(parent, "" + index, index);
        this.index = index;
        addHandler(
                new Handler(new ComponentModel(new BundleInstantiationSpecification(
                    new ComponentSpecification("clustercontroller-status"),
                    new ComponentSpecification("com.yahoo.vespa.clustercontroller.apps.clustercontroller.StatusHandler"),
                    CLUSTERCONTROLLER_BUNDLE))), "clustercontroller-status/*"
        );
        addHandler(
                new Handler(new ComponentModel(new BundleInstantiationSpecification(
                        new ComponentSpecification("clustercontroller-state-restapi-v2"),
                        new ComponentSpecification("com.yahoo.vespa.clustercontroller.apps.clustercontroller.StateRestApiV2Handler"),
                        CLUSTERCONTROLLER_BUNDLE))), "cluster/v2/*"
        );
        if (runStandaloneZooKeeper) {
            addComponent(new Component<>(new ComponentModel(new BundleInstantiationSpecification(
                    new ComponentSpecification("clustercontroller-zkrunner"),
                    new ComponentSpecification("com.yahoo.vespa.zookeeper.ZooKeeperServer"), ZKFACADE_BUNDLE))));
            addComponent(new Component<>(new ComponentModel(new BundleInstantiationSpecification(
                    new ComponentSpecification("clustercontroller-zkprovider"),
                    new ComponentSpecification("com.yahoo.vespa.clustercontroller.apps.clustercontroller.StandaloneZooKeeperProvider"), CLUSTERCONTROLLER_BUNDLE))));
        } else {
            addComponent(new Component<>(new ComponentModel(new BundleInstantiationSpecification(
                    new ComponentSpecification("clustercontroller-zkprovider"),
                    new ComponentSpecification("com.yahoo.vespa.clustercontroller.apps.clustercontroller.DummyZooKeeperProvider"), CLUSTERCONTROLLER_BUNDLE))));
        }
        addBundle("file:" + getDefaults().underVespaHome("lib/jars/clustercontroller-apps-jar-with-dependencies.jar"));
        addBundle("file:" + getDefaults().underVespaHome("lib/jars/clustercontroller-apputil-jar-with-dependencies.jar"));
        addBundle("file:" + getDefaults().underVespaHome("lib/jars/clustercontroller-core-jar-with-dependencies.jar"));
        addBundle("file:" + getDefaults().underVespaHome("lib/jars/clustercontroller-utils-jar-with-dependencies.jar"));
        addBundle("file:" + getDefaults().underVespaHome("lib/jars/zkfacade-jar-with-dependencies.jar"));

        log.log(LogLevel.DEBUG, "Adding access log for cluster controller ...");
        addComponent(new AccessLogComponent(AccessLogComponent.AccessLogType.queryAccessLog, "controller", stateIsHosted(deployStateFrom(parent))));
    }

    @Override
    public int getWantedPort() {
        return 19050;
    }

    @Override
    public boolean requiresWantedPort() {
        return index == 0;
    }

    @Override
    public String getServiceType() {
        return "container-clustercontroller";
    }

    private void addHandler(Handler h, String binding) {
        h.addServerBindings("http://*/" + binding,
                            "https://*/" + binding);
        super.addHandler(h);
    }

    public void addBundle(String bundlePath) {
        bundles.add(bundlePath);
    }

    @Override
    public void getConfig(BundlesConfig.Builder builder) {
        for (String bundle : bundles) {
            builder.bundle(bundle);
        }
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        builder.myid(index);
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        builder.jvm(new QrStartConfig.Jvm.Builder().heapsize(512));
    }

    int getIndex() {
        return index;
    }
}
