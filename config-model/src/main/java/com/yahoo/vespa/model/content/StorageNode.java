// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorBucketmoverConfig;
import com.yahoo.vespa.config.storage.StorDevicesConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.config.storage.StorMemfilepersistenceConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;
import com.yahoo.vespa.model.content.engines.ProtonProvider;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import org.w3c.dom.Element;

import java.util.Arrays;

/**
 * Class to provide config related to a specific storage node.
 */
@RestartConfigs({StorDevicesConfig.class, StorFilestorConfig.class,
                 StorMemfilepersistenceConfig.class, StorBucketmoverConfig.class})
public class StorageNode extends ContentNode implements StorServerConfig.Producer, StorDevicesConfig.Producer {

    static final String rootFolder = Defaults.getDefaults().underVespaHome("var/db/vespa/vds/");

    private final Double capacity;
    private final boolean retired;
    private final boolean isHostedVespa;
    private boolean usesVdsEngine = false;

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
        this.isHostedVespa = cluster.getRoot().getDeployState().getProperties().hostedVespa();
    }

    @Override
    public String getStartupCommand() {
        return isProviderProton()
                ? null
                : "exec sbin/vespa-storaged -c $VESPA_CONFIG_ID";
    }

    @Override
    public void getConfig(StorDevicesConfig.Builder builder) {
        String root_folder = getRootDirectory();
        builder.root_folder(root_folder);

        // For VDS in hosted Vespa, we default to using the root_folder as the disk to store the data in.
        // Setting disk_path will then
        if (isHostedVespa && usesVdsEngine) {
            // VDS looks up the first disk at the directory path root_folder/disks/d0.
            builder.disk_path(Arrays.asList(root_folder + "/disks/d0"));
        }
    }

    // 2015-08-11: Needed because of the following circular dependency:
    // 1. StorageNode is created.
    // 2. A particular persistence engine is picked depending on things (like the presence of engine/proton element)
    //    that are hidden from the code creating the StorageNode in (1).
    // 3. The persistence engine depends on the StorageNode, e.g. it's a parent node.
    //
    // If the VDSEngine is picked in (2), we would like to know this in StorageNode::getConfig(). Hence this setter.
    public void useVdsEngine() {
        usesVdsEngine = true;
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
