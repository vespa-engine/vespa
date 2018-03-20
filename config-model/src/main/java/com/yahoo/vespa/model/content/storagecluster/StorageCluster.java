// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.vespa.config.content.core.StorIntegritycheckerConfig;
import com.yahoo.vespa.config.storage.StorMemfilepersistenceConfig;
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
        StorMemfilepersistenceConfig.Producer,
        StorIntegritycheckerConfig.Producer,
        StorFilestorConfig.Producer,
        StorVisitorConfig.Producer,
        PersistenceConfig.Producer,
        MetricsmanagerConfig.Producer
{
    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<StorageCluster> {
        @Override
        protected StorageCluster doBuild(AbstractConfigProducer ancestor, Element producerSpec) {
            final ModelElement clusterElem = new ModelElement(producerSpec);
            final ContentCluster cluster = (ContentCluster)ancestor;

            return new StorageCluster(ancestor,
                                      ContentCluster.getClusterName(clusterElem),
                                      new FileStorProducer.Builder().build(cluster, clusterElem),
                                      new IntegrityCheckerProducer.Builder().build(cluster, clusterElem),
                                      new StorServerProducer.Builder().build(clusterElem),
                                      new StorVisitorProducer.Builder().build(clusterElem),
                                      new PersistenceProducer.Builder().build(clusterElem));
        }
    }

    private Integer bucketMoverMaxFillAboveAverage = null;
    private Long cacheSize = null;
    private Double diskFullPercentage = null;

    private String clusterName;
    private FileStorProducer fileStorProducer;
    private IntegrityCheckerProducer integrityCheckerProducer;
    private StorServerProducer storServerProducer;
    private StorVisitorProducer storVisitorProducer;
    private PersistenceProducer persistenceProducer;

    StorageCluster(AbstractConfigProducer parent,
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
        if (bucketMoverMaxFillAboveAverage != null) {
            builder.max_target_fill_rate_above_average(bucketMoverMaxFillAboveAverage);
        }
    }

    @Override
    public void getConfig(StorMemfilepersistenceConfig.Builder builder) {
        if (cacheSize != null) {
            builder.cache_size(cacheSize);
        }

        if (diskFullPercentage != null) {
            builder.disk_full_factor(diskFullPercentage / 100.0);
            builder.disk_full_factor_move(diskFullPercentage / 100.0 * 0.9);
        }
    }

    @Override
    public void getConfig(MetricsmanagerConfig.Builder builder) {
        ContentCluster.getMetricBuilder("fleetcontroller", builder).
                addedmetrics("vds.datastored.alldisks.docs").
                addedmetrics("vds.datastored.alldisks.bytes").
                addedmetrics("vds.datastored.alldisks.buckets");

        ContentCluster.getMetricBuilder("log", builder).
                addedmetrics("vds.filestor.alldisks.allthreads.put.sum").
                addedmetrics("vds.filestor.alldisks.allthreads.get.sum").
                addedmetrics("vds.filestor.alldisks.allthreads.remove.sum").
                addedmetrics("vds.filestor.alldisks.allthreads.update.sum").
                addedmetrics("vds.datastored.alldisks.docs").
                addedmetrics("vds.datastored.alldisks.bytes").
                addedmetrics("vds.filestor.alldisks.queuesize").
                addedmetrics("vds.filestor.alldisks.averagequeuewait.sum").
                addedmetrics("vds.visitor.cv_queuewaittime").
                addedmetrics("vds.visitor.allthreads.averagequeuewait").
                addedmetrics("vds.visitor.allthreads.averagevisitorlifetime").
                addedmetrics("vds.visitor.allthreads.created.sum");
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
    }
}
