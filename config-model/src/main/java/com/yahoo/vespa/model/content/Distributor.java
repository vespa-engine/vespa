// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;
import org.w3c.dom.Element;

/**
 * Represents specific configuration for a given distributor node.
 */
public class Distributor extends ContentNode implements StorDistributormanagerConfig.Producer {

    PersistenceEngine provider;
    private final int numDistributorStripesFlag;

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<Distributor> {
        ModelElement clusterXml;
        PersistenceEngine persistenceProvider;

        public Builder(ModelElement clusterXml, PersistenceEngine persistenceProvider) {
            this.clusterXml = clusterXml;
            this.persistenceProvider = persistenceProvider;
        }

        @Override
        protected Distributor doBuild(DeployState deployState, AbstractConfigProducer ancestor, Element producerSpec) {
            return new Distributor(deployState.getProperties(), (DistributorCluster)ancestor, new ModelElement(producerSpec).integerAttribute("distribution-key"),
                                   clusterXml.integerAttribute("distributor-base-port"), persistenceProvider);
        }
    }

    Distributor(ModelContext.Properties properties, DistributorCluster parent, int distributionKey, Integer distributorBasePort, PersistenceEngine provider) {
        super(properties.featureFlags(), parent, parent.getClusterName(),
             StorageNode.rootFolder + parent.getClusterName() + "/distributor/" + distributionKey, distributionKey);

        this.provider = provider;
        this.numDistributorStripesFlag = properties.featureFlags().numDistributorStripes();

        if (distributorBasePort != null) {
            setBasePort(distributorBasePort);
        }
    }

    private int tuneNumDistributorStripes() {
        if (numDistributorStripesFlag == -1) {
            if (getHostResource() != null) {
                int cores = (int)getHostResource().realResources().vcpu();
                if (cores <= 16) {
                    return 1;
                } else if (cores <= 64) {
                    return 2;
                } else {
                    return 4;
                }
            } else {
                return 1;
            }
        }
        return numDistributorStripesFlag;
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        super.getConfig(builder);
        provider.getConfig(builder);
    }

    @Override
    public void getConfig(StorCommunicationmanagerConfig.Builder builder) {
        super.getConfig(builder);
        // Single distributor needs help to encode the messages.
        builder.mbus.dispatch_on_encode(true);
    }

    @Override
    public void getConfig(StorDistributormanagerConfig.Builder builder) {
        builder.num_distributor_stripes(tuneNumDistributorStripes());
    }

    @Override
    public String getStartupCommand() {
        return "exec sbin/vespa-distributord -c $VESPA_CONFIG_ID";
    }

}
