// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

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


    private static class GcOptions {
        public final int interval;
        public final String selection;

        public GcOptions(int interval, String selection) {
            this.interval = interval;
            this.selection = selection;
        }
    }

    private final ContentCluster parent;
    private final BucketSplitting bucketSplitting;
    private final GcOptions gc;
    private final boolean hasIndexedDocumentType;

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<DistributorCluster> {
        ContentCluster parent;

        public Builder(ContentCluster parent) {
            this.parent = parent;
        }

        private String prepareGCSelection(ModelElement documentNode, String selStr) throws ParseException {
            DocumentSelector s = new DocumentSelector(selStr);
            boolean enableGC = false;
            if (documentNode != null) {
                enableGC = documentNode.getBooleanAttribute("garbage-collection", false);
            }
            if (!enableGC) {
                return null;
            }

            return s.toString();
        }

        private int getGCInterval(ModelElement documentNode) throws ParseException {
            int gcInterval = 3600;
            if (documentNode != null) {
                gcInterval = documentNode.getIntegerAttribute("garbage-collection-interval", gcInterval);
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
            // The presence of at least one <document> and its mode attribute is schema-enforced.
            return documentsNode.subElements("document").stream()
                    .anyMatch(node -> documentModeImpliesIndexing(node.getStringAttribute("mode")));
        }

        @Override
        protected DistributorCluster doBuild(AbstractConfigProducer ancestor, Element producerSpec) {
            final ModelElement clusterElement = new ModelElement(producerSpec);
            final ModelElement documentsNode = clusterElement.getChild("documents");
            final GcOptions gc = parseGcOptions(documentsNode);
            final boolean hasIndexedDocumentType = clusterContainsIndexedDocumentType(documentsNode);

            return new DistributorCluster(parent,
                    new BucketSplitting.Builder().build(new ModelElement(producerSpec)), gc, hasIndexedDocumentType);
        }
    }

    private DistributorCluster(ContentCluster parent, BucketSplitting bucketSplitting,
                               GcOptions gc, boolean hasIndexedDocumentType)
    {
        super(parent, "distributor");
        this.parent = parent;
        this.bucketSplitting = bucketSplitting;
        this.gc = gc;
        this.hasIndexedDocumentType = hasIndexedDocumentType;
    }

    @Override
    public void getConfig(StorDistributormanagerConfig.Builder builder) {
        if (gc.selection != null) {
            builder.garbagecollection(new StorDistributormanagerConfig.Garbagecollection.Builder()
                    .selectiontoremove("not (" + gc.selection + ")")
                    .interval(gc.interval));
        }
        builder.enable_revert(parent.getPersistence().supportRevert());
        builder.disable_bucket_activation(hasIndexedDocumentType == false);

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
