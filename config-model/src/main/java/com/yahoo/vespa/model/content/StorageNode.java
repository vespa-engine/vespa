// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorBucketmoverConfig;
import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;
import com.yahoo.vespa.model.content.engines.ProtonProvider;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import org.w3c.dom.Element;

/**
 * Class to provide config related to a specific storage node.
 */
@RestartConfigs({StorFilestorConfig.class, StorBucketmoverConfig.class})
public class StorageNode extends ContentNode implements StorServerConfig.Producer, StorFilestorConfig.Producer {

    static final String rootFolder = Defaults.getDefaults().underVespaHome("var/db/vespa/search/");

    private final Double capacity;
    private final boolean retired;
    private final StorageCluster cluster;

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<StorageNode> {
        @Override
        protected StorageNode doBuild(DeployState deployState, AbstractConfigProducer ancestor, Element producerSpec) {
            ModelElement e = new ModelElement(producerSpec);
            return new StorageNode(deployState.getProperties(), (StorageCluster)ancestor, e.doubleAttribute("capacity"), e.integerAttribute("distribution-key"), false);
        }
    }

    StorageNode(ModelContext.Properties properties, StorageCluster cluster, Double capacity, int distributionKey, boolean retired) {
        super(properties.featureFlags(), cluster, cluster.getClusterName(),
              rootFolder + cluster.getClusterName() + "/storage/" + distributionKey,
              distributionKey);
        this.retired = retired;
        this.capacity = capacity;
        this.cluster = cluster;
    }

    @Override
    public String getStartupCommand() {
        return isProviderProton()
                ? null
                : "exec sbin/vespa-storaged -c $VESPA_CONFIG_ID";
    }

    public double getCapacity() {
        if (capacity != null) {
            return capacity;
        } else {
            return 1.0;
        }
    }

    /** Whether this node is configured as retired, which means all content should migrate off the node */
    public boolean isRetired() { return retired; }

    private boolean isProviderProton() {
        for (AbstractConfigProducer producer : getChildren().values()) {
            if (producer instanceof ProtonProvider) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        super.getConfig(builder);

        builder.node_capacity(getCapacity());

        for (AbstractConfigProducer producer : getChildren().values()) {
            ((PersistenceEngine)producer).getConfig(builder);
        }
    }

    @Override
    public void getConfig(StorFilestorConfig.Builder builder) {
        if (getHostResource() != null && ! getHostResource().realResources().isUnspecified()) {
            builder.num_threads(Math.max(4, (int)getHostResource().realResources().vcpu()));
        }
        cluster.getConfig(builder);
    }

    @Override
    public void getConfig(StorCommunicationmanagerConfig.Builder builder) {
        super.getConfig(builder);
        builder.mbus.dispatch_on_encode(false);
    }

}
