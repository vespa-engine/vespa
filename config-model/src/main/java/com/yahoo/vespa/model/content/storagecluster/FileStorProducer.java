// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Serves stor-filestor for storage clusters.
 */
public class FileStorProducer implements StorFilestorConfig.Producer {

    public static class Builder {
        protected FileStorProducer build(ModelContext.Properties properties, ContentCluster parent, ModelElement clusterElem) {
            return new FileStorProducer(properties.featureFlags(), parent, getThreads(clusterElem));
        }

       private Integer getThreads(ModelElement clusterElem) {
           ModelElement tuning = clusterElem.child("tuning");
           if (tuning == null) {
               return null;
           }
           ModelElement threads = tuning.child("persistence-threads");
           if (threads == null) {
               return null;
           }

           Integer count = threads.integerAttribute("count");
           if (count != null) {
               return count;
           }

           // Backward compatible fallback
           int numThreads = 0;
           for (ModelElement thread : threads.subElements("thread")) {
               count = thread.integerAttribute("count");
               numThreads += (count == null) ? 1 : count;
           }

           return numThreads;
       }
    }

    private final Integer numThreads;
    private final ContentCluster cluster;
    private final int reponseNumThreads;
    private final StorFilestorConfig.Response_sequencer_type.Enum responseSequencerType;
    private final double persistenceThrottlingWsDecrementFactor;
    private final double persistenceThrottlingWsBackoff;
    private final int persistenceThrottingWindowSize;
    private final double persistenceThrottlingWsResizeRate;
    private final boolean persistenceThrottlingOfMergeFeedOps;
    private final boolean useAsyncMessageHandlingOnSchedule;

    private static StorFilestorConfig.Response_sequencer_type.Enum convertResponseSequencerType(String sequencerType) {
        try {
            return StorFilestorConfig.Response_sequencer_type.Enum.valueOf(sequencerType);
        } catch (Throwable t) {
            return StorFilestorConfig.Response_sequencer_type.Enum.ADAPTIVE;
        }
    }

    public FileStorProducer(ModelContext.FeatureFlags featureFlags, ContentCluster parent, Integer numThreads) {
        this.numThreads = numThreads;
        this.cluster = parent;
        this.reponseNumThreads = featureFlags.defaultNumResponseThreads();
        this.responseSequencerType = convertResponseSequencerType(featureFlags.responseSequencerType());
        this.persistenceThrottlingWsDecrementFactor = featureFlags.persistenceThrottlingWsDecrementFactor();
        this.persistenceThrottlingWsBackoff = featureFlags.persistenceThrottlingWsBackoff();
        this.persistenceThrottingWindowSize = featureFlags.persistenceThrottlingWindowSize();
        this.persistenceThrottlingWsResizeRate = featureFlags.persistenceThrottlingWsResizeRate();
        this.persistenceThrottlingOfMergeFeedOps = featureFlags.persistenceThrottlingOfMergeFeedOps();
        this.useAsyncMessageHandlingOnSchedule = featureFlags.useAsyncMessageHandlingOnSchedule();
    }

    @Override
    public void getConfig(StorFilestorConfig.Builder builder) {
        if (numThreads != null) {
            builder.num_threads(numThreads);
        }
        builder.enable_multibit_split_optimalization(cluster.getPersistence().enableMultiLevelSplitting());
        builder.num_response_threads(reponseNumThreads);
        builder.response_sequencer_type(responseSequencerType);
        builder.use_async_message_handling_on_schedule(useAsyncMessageHandlingOnSchedule);
        var throttleBuilder = new StorFilestorConfig.Async_operation_throttler.Builder();
        throttleBuilder.window_size_decrement_factor(persistenceThrottlingWsDecrementFactor);
        throttleBuilder.window_size_backoff(persistenceThrottlingWsBackoff);
        if (persistenceThrottingWindowSize > 0) {
            throttleBuilder.min_window_size(persistenceThrottingWindowSize);
            throttleBuilder.max_window_size(persistenceThrottingWindowSize);
        }
        throttleBuilder.resize_rate(persistenceThrottlingWsResizeRate);
        throttleBuilder.throttle_individual_merge_feed_ops(persistenceThrottlingOfMergeFeedOps);
        builder.async_operation_throttler(throttleBuilder);
    }

}
