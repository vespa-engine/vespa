// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.config.content.core.StorIntegritycheckerConfig;
import com.yahoo.vespa.config.content.core.StorBucketmoverConfig;
import com.yahoo.vespa.config.content.core.StorVisitorConfig;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.content.PersistenceConfig;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.StorageNode;
import org.w3c.dom.Element;

/**
 * Represents configuration that is common to all storage nodes.
 */
public class StorageCluster extends AbstractConfigProducer<StorageNode>
    implements StorServerConfig.Producer,
        StorBucketmoverConfig.Producer,
        StorIntegritycheckerConfig.Producer,
        StorFilestorConfig.Producer,
        StorVisitorConfig.Producer,
        PersistenceConfig.Producer,
        MetricsmanagerConfig.Producer
{
    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<StorageCluster> {
        @Override
        protected StorageCluster doBuild(DeployState deployState, AbstractConfigProducer<?> ancestor, Element producerSpec) {
            final ModelElement clusterElem = new ModelElement(producerSpec);
            final ContentCluster cluster = (ContentCluster)ancestor;

            return new StorageCluster(ancestor,
                                      ContentCluster.getClusterId(clusterElem),
                                      new FileStorProducer.Builder().build(deployState.getProperties(), cluster, clusterElem),
                                      new IntegrityCheckerProducer.Builder().build(cluster, clusterElem),
                                      new StorServerProducer.Builder().build(clusterElem),
                                      new StorVisitorProducer.Builder().build(clusterElem),
                                      new PersistenceProducer.Builder().build(clusterElem));
        }
    }

    private final String clusterName;
    private final FileStorProducer fileStorProducer;
    private final IntegrityCheckerProducer integrityCheckerProducer;
    private final StorServerProducer storServerProducer;
    private final StorVisitorProducer storVisitorProducer;
    private final PersistenceProducer persistenceProducer;

    StorageCluster(AbstractConfigProducer<?> parent,
                   String clusterName,
                   FileStorProducer fileStorProducer,
                   IntegrityCheckerProducer integrityCheckerProducer,
                   StorServerProducer storServerProducer,
                   StorVisitorProducer storVisitorProducer,
                   PersistenceProducer persistenceProducer) {
        super(parent, "storage");
        this.clusterName = clusterName;
        this.fileStorProducer = fileStorProducer;
        this.integrityCheckerProducer = integrityCheckerProducer;
        this.storServerProducer = storServerProducer;
        this.storVisitorProducer = storVisitorProducer;
        this.persistenceProducer = persistenceProducer;
    }

    @Override
    public void getConfig(StorBucketmoverConfig.Builder builder) {
    }

    @Override
    public void getConfig(MetricsmanagerConfig.Builder builder) {
        ContentCluster.getMetricBuilder("fleetcontroller", builder).
                addedmetrics("vds.datastored.alldisks.docs").
                addedmetrics("vds.datastored.alldisks.bytes").
                addedmetrics("vds.datastored.alldisks.buckets").
                addedmetrics("vds.datastored.bucket_space.buckets_total");

        ContentCluster.getMetricBuilder("log", builder).
                addedmetrics("vds.filestor.allthreads.put").
                addedmetrics("vds.filestor.allthreads.get").
                addedmetrics("vds.filestor.allthreads.remove").
                addedmetrics("vds.filestor.allthreads.update").
                addedmetrics("vds.datastored.alldisks.docs").
                addedmetrics("vds.datastored.alldisks.bytes").
                addedmetrics("vds.filestor.queuesize").
                addedmetrics("vds.filestor.averagequeuewait").
                addedmetrics("vds.visitor.cv_queuewaittime").
                addedmetrics("vds.visitor.allthreads.averagequeuewait").
                addedmetrics("vds.visitor.allthreads.averagevisitorlifetime").
                addedmetrics("vds.visitor.allthreads.created");
    }

    public String getClusterName() {
        return clusterName;
    }

    @Override
    public void getConfig(StorIntegritycheckerConfig.Builder builder) {
        integrityCheckerProducer.getConfig(builder);
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        storServerProducer.getConfig(builder);
    }

    @Override
    public void getConfig(StorVisitorConfig.Builder builder) {
        storVisitorProducer.getConfig(builder);
    }

    @Override
    public void getConfig(PersistenceConfig.Builder builder) {
        persistenceProducer.getConfig(builder);
    }

    @Override
    public void getConfig(StorFilestorConfig.Builder builder) {
        fileStorProducer.getConfig(builder);
        storVisitorProducer.getConfig(builder);
    }

}
