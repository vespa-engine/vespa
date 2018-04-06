// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorBucketmoverConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
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
public class StorageNode extends ContentNode implements StorServerConfig.Producer {

    static final String rootFolder = Defaults.getDefaults().underVespaHome("var/db/vespa/search/");

    private final Double capacity;
    private final boolean retired;

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<StorageNode> {
        @Override
        protected StorageNode doBuild(AbstractConfigProducer ancestor, Element producerSpec) {
            ModelElement e = new ModelElement(producerSpec);
            return new StorageNode((StorageCluster)ancestor, e.getDoubleAttribute("capacity"), e.getIntegerAttribute("distribution-key"), false);
        }
    }

    StorageNode(StorageCluster cluster, Double capacity, int distributionKey, boolean retired) {
        super(cluster,
              cluster.getClusterName(),
              rootFolder + cluster.getClusterName() + "/storage/" + distributionKey,
              distributionKey);
        this.retired = retired;
        this.capacity = capacity;
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

}
