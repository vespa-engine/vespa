// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

/**
 * Serves stor-filestor for storage clusters.
 */
public class FileStorProducer implements StorFilestorConfig.Producer {

    public static class Builder {
        protected FileStorProducer build(ModelContext.Properties properties, ModelElement clusterElem) {
            return new FileStorProducer(properties.featureFlags(), getThreads(clusterElem));
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
    private final int responseNumThreads;
    private final StorFilestorConfig.Response_sequencer_type.Enum responseSequencerType;
    private final boolean useAsyncMessageHandlingOnSchedule;
    private final int maxContentNodeMaintenanceOpConcurrency;

    private static StorFilestorConfig.Response_sequencer_type.Enum convertResponseSequencerType(String sequencerType) {
        try {
            return StorFilestorConfig.Response_sequencer_type.Enum.valueOf(sequencerType);
        } catch (Throwable t) {
            return StorFilestorConfig.Response_sequencer_type.Enum.ADAPTIVE;
        }
    }

    public FileStorProducer(ModelContext.FeatureFlags featureFlags, Integer numThreads) {
        this.numThreads = numThreads;
        this.responseNumThreads = featureFlags.defaultNumResponseThreads();
        this.responseSequencerType = convertResponseSequencerType(featureFlags.responseSequencerType());
        this.useAsyncMessageHandlingOnSchedule = featureFlags.useAsyncMessageHandlingOnSchedule();
        this.maxContentNodeMaintenanceOpConcurrency = featureFlags.maxContentNodeMaintenanceOpConcurrency();
    }


    @Override
    public void getConfig(StorFilestorConfig.Builder builder) {
        if (numThreads != null) {
            builder.num_threads(numThreads);
        }
        builder.num_response_threads(responseNumThreads);
        builder.response_sequencer_type(responseSequencerType);
        builder.use_async_message_handling_on_schedule(useAsyncMessageHandlingOnSchedule);
        var throttleBuilder = new StorFilestorConfig.Async_operation_throttler.Builder();
        builder.async_operation_throttler(throttleBuilder);
        if (maxContentNodeMaintenanceOpConcurrency > 0) {
            var maintenanceThrottleBuilder = new StorFilestorConfig.Maintenance_operation_throttler.Builder();
            maintenanceThrottleBuilder.type(StorFilestorConfig.Maintenance_operation_throttler.Type.DYNAMIC);
            maintenanceThrottleBuilder.max_window_size(maxContentNodeMaintenanceOpConcurrency);
            builder.maintenance_operation_throttler(maintenanceThrottleBuilder);
        }
        builder.max_feed_op_batch_size(64); // TODO: Remove when default in config definition is 64
    }

}
