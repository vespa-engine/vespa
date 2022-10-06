// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.document.select.DocumentSelector;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import org.w3c.dom.Element;

import java.util.logging.Logger;

/**
 * Generates distributor-specific configuration.
 */
public class DistributorCluster extends AbstractConfigProducer<Distributor> implements
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
    private final boolean useTwoPhaseDocumentGc;

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<DistributorCluster> {

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
        protected DistributorCluster doBuild(DeployState deployState, AbstractConfigProducer<?> ancestor, Element producerSpec) {
            final ModelElement clusterElement = new ModelElement(producerSpec);
            final ModelElement documentsNode = clusterElement.child("documents");
            final GcOptions gc = parseGcOptions(documentsNode);
            final boolean hasIndexedDocumentType = clusterContainsIndexedDocumentType(documentsNode);
            int maxInhibitedGroups = deployState.getProperties().featureFlags().maxActivationInhibitedOutOfSyncGroups();
            boolean useTwoPhaseDocumentGc = deployState.getProperties().featureFlags().useTwoPhaseDocumentGc();

            return new DistributorCluster(parent,
                    new BucketSplitting.Builder().build(new ModelElement(producerSpec)), gc,
                    hasIndexedDocumentType,
                    maxInhibitedGroups, useTwoPhaseDocumentGc);
        }
    }

    private DistributorCluster(ContentCluster parent, BucketSplitting bucketSplitting,
                               GcOptions gc, boolean hasIndexedDocumentType,
                               int maxActivationInhibitedOutOfSyncGroups,
                               boolean useTwoPhaseDocumentGc)
    {
        super(parent, "distributor");
        this.parent = parent;
        this.bucketSplitting = bucketSplitting;
        this.gc = gc;
        this.hasIndexedDocumentType = hasIndexedDocumentType;
        this.maxActivationInhibitedOutOfSyncGroups = maxActivationInhibitedOutOfSyncGroups;
        this.useTwoPhaseDocumentGc = useTwoPhaseDocumentGc;
    }

    @Override
    public void getConfig(StorDistributormanagerConfig.Builder builder) {
        if (gc.selection != null) {
            builder.garbagecollection(new StorDistributormanagerConfig.Garbagecollection.Builder()
                    .selectiontoremove("not (" + gc.selection + ")")
                    .interval(gc.interval));
        }
        builder.enable_revert(parent.getPersistence().supportRevert());
        builder.disable_bucket_activation(!hasIndexedDocumentType);
        builder.max_activation_inhibited_out_of_sync_groups(maxActivationInhibitedOutOfSyncGroups);
        builder.enable_two_phase_garbage_collection(useTwoPhaseDocumentGc);

        bucketSplitting.getConfig(builder);
    }

    @Override
    public void getConfig(MetricsmanagerConfig.Builder builder) {
        ContentCluster.getMetricBuilder("log", builder).
                addedmetrics("vds.distributor.docsstored").
                addedmetrics("vds.distributor.bytesstored").
                addedmetrics("vds.idealstate.delete_bucket.done_ok").
                addedmetrics("vds.idealstate.merge_bucket.done_ok").
                addedmetrics("vds.idealstate.split_bucket.done_ok").
                addedmetrics("vds.idealstate.join_bucket.done_ok").
                addedmetrics("vds.idealstate.buckets_rechecking");
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
}
