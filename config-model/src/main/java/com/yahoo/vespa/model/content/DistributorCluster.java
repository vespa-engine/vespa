// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import ai.vespa.metrics.DistributorMetrics;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.document.select.DocumentSelector;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.vespa.model.builder.xml.dom.BinaryUnit;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import org.w3c.dom.Element;

import java.util.logging.Logger;

import static java.util.logging.Level.WARNING;

/**
 * Generates distributor-specific configuration.
 */
public class DistributorCluster extends TreeConfigProducer<Distributor> implements
        StorDistributormanagerConfig.Producer,
        StorServerConfig.Producer,
        MetricsmanagerConfig.Producer {

    public static final Logger log = Logger.getLogger(DistributorCluster.class.getPackage().toString());

    private record GcOptions(int interval, String selection) { }

    private final ContentCluster parent;
    private final BucketSplitting bucketSplitting;
    private final GcOptions gc;
    private final boolean hasIndexedDocumentType;
    private final int maxActivationInhibitedOutOfSyncGroups;
    private final int contentLayerMetadataFeatureLevel;
    private final int maxDocumentOperationSizeMib;

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilderBase<DistributorCluster> {

        ContentCluster parent;

        public Builder(ContentCluster parent) {
            this.parent = parent;
        }

        private String prepareGCSelection(ModelElement documentNode, String selectionString) throws ParseException {
            DocumentSelector s = new DocumentSelector(selectionString);
            boolean enableGC = false;
            if (documentNode != null) {
                enableGC = documentNode.booleanAttribute("garbage-collection", false);
            }
            if (!enableGC) {
                return null;
            }

            return s.toString();
        }

        private int getGCInterval(ModelElement documentNode) {
            int gcInterval = 3600;
            if (documentNode != null) {
                gcInterval = documentNode.integerAttribute("garbage-collection-interval", gcInterval);
            }
            return gcInterval;
        }

        private GcOptions parseGcOptions(ModelElement documentNode) {
            String gcSelection = parent.getRoutingSelector();
            int gcInterval;
            try {
                if (gcSelection != null) {
                    gcSelection = prepareGCSelection(documentNode, gcSelection);
                }
                gcInterval = getGCInterval(documentNode);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Failed to parse garbage collection selection", e);
            }
            return new GcOptions(gcInterval, gcSelection);
        }

        private boolean documentModeImpliesIndexing(String mode) {
            return "index".equals(mode);
        }

        private boolean clusterContainsIndexedDocumentType(ModelElement documentsNode) {
            return documentsNode != null
                    && documentsNode.subElements("document").stream()
                    .anyMatch(node -> documentModeImpliesIndexing(node.stringAttribute("mode")));
        }

        @Override
        protected DistributorCluster doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec) {
            final ModelElement clusterElement = new ModelElement(producerSpec);
            final ModelElement documentsNode = clusterElement.child("documents");
            final GcOptions gc = parseGcOptions(documentsNode);
            final boolean hasIndexedDocumentType = clusterContainsIndexedDocumentType(documentsNode);
            var featureFlags = deployState.getProperties().featureFlags();
            int maxInhibitedGroups = featureFlags.maxActivationInhibitedOutOfSyncGroups();
            int contentLayerMetadataFeatureLevel = featureFlags.contentLayerMetadataFeatureLevel();
            int maxDocumentOperationSizeMib = maxDocumentSizeInMib(featureFlags.maxDistributorDocumentOperationSizeMib(),
                                                                   clusterElement,
                                                                   deployState.getDeployLogger());

            return new DistributorCluster(parent,
                    new BucketSplitting.Builder().build(new ModelElement(producerSpec)), gc,
                    hasIndexedDocumentType,
                    maxInhibitedGroups,
                    contentLayerMetadataFeatureLevel,
                    maxDocumentOperationSizeMib);
        }
    }

    private DistributorCluster(ContentCluster parent, BucketSplitting bucketSplitting,
                               GcOptions gc, boolean hasIndexedDocumentType,
                               int maxActivationInhibitedOutOfSyncGroups,
                               int contentLayerMetadataFeatureLevel,
                               int maxDocumentOperationSizeMib)
    {
        super(parent, "distributor");
        this.parent = parent;
        this.bucketSplitting = bucketSplitting;
        this.gc = gc;
        this.hasIndexedDocumentType = hasIndexedDocumentType;
        this.maxActivationInhibitedOutOfSyncGroups = maxActivationInhibitedOutOfSyncGroups;
        this.contentLayerMetadataFeatureLevel = contentLayerMetadataFeatureLevel;
        this.maxDocumentOperationSizeMib = maxDocumentOperationSizeMib;
    }

    @Override
    public void getConfig(StorDistributormanagerConfig.Builder builder) {
        if (gc.selection != null) {
            builder.garbagecollection(new StorDistributormanagerConfig.Garbagecollection.Builder()
                    .selectiontoremove("not (" + gc.selection + ")")
                    .interval(gc.interval));
        }
        builder.disable_bucket_activation(!hasIndexedDocumentType);
        builder.max_activation_inhibited_out_of_sync_groups(maxActivationInhibitedOutOfSyncGroups);
        if (contentLayerMetadataFeatureLevel > 0) {
            builder.enable_operation_cancellation(true);
        }
        // TODO: Unnecessary, remove after config definition default value is changed to true
        builder.symmetric_put_and_activate_replica_selection(true);

        if (maxDocumentOperationSizeMib > 0 && maxDocumentOperationSizeMib < 2048) {
            builder.max_document_operation_message_size_bytes(maxDocumentOperationSizeMib * 1024 * 1024);
        }
        bucketSplitting.getConfig(builder);
    }

    @Override
    public void getConfig(MetricsmanagerConfig.Builder builder) {
        ContentCluster.getMetricBuilder("log", builder).
                addedmetrics(DistributorMetrics.VDS_DISTRIBUTOR_DOCSSTORED.baseName()).
                addedmetrics(DistributorMetrics.VDS_DISTRIBUTOR_BYTESSTORED.baseName()).
                addedmetrics(DistributorMetrics.VDS_IDEALSTATE_DELETE_BUCKET_DONE_OK.baseName()).
                addedmetrics(DistributorMetrics.VDS_IDEALSTATE_MERGE_BUCKET_DONE_OK.baseName()).
                addedmetrics(DistributorMetrics.VDS_IDEALSTATE_SPLIT_BUCKET_DONE_OK.baseName()).
                addedmetrics(DistributorMetrics.VDS_IDEALSTATE_JOIN_BUCKET_DONE_OK.baseName()).
                addedmetrics(DistributorMetrics.VDS_IDEALSTATE_BUCKETS_RECHECKING.baseName());
    }

    @Override
    public void getConfig(StorServerConfig.Builder builder) {
        builder.root_folder("");
        builder.cluster_name(parent.getName());
        builder.is_distributor(true);
    }

    public String getClusterName() {
        return parent.getName();
    }

    private static int maxDocumentSizeInMib(int featureFlagValue, ModelElement clusterElement, DeployLogger deployLogger) {
        var tuning = clusterElement.child("tuning");
        if (tuning == null) return featureFlagValue;
        var maxSize = tuning.child("max-document-size");
        if (maxSize == null) return featureFlagValue;

        var configuredValue = maxSize.asString();
        int maxDocumentSize = featureFlagValue;
        if (configuredValue != null && ! configuredValue.isEmpty()) {
            // The configured value has units, but the config expects it in MiB, extract the value and convert
            maxDocumentSize = (int) (BinaryUnit.valueOf(configuredValue) / 1024 / 1024);
            if (maxDocumentSize < 1 || maxDocumentSize > 2048)
                throw new IllegalArgumentException("Invalid max-document-size value '" + configuredValue + "': Value must be between 1 MiB and 2048 MiB");
            if (maxDocumentSize > 128)
                deployLogger.log(WARNING, "max-document-size value is set to '" + configuredValue +
                        "', setting this above 128 MiB is strongly discouraged, as it may cause major performance issues. " +
                        "See https://docs.vespa.ai/en/reference/services-content.html#max-document-size");
        }
        return maxDocumentSize;
    }

}
