// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorVisitorConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;

/**
 * Serves stor-visitor config for storage clusters.
 */
public class StorVisitorProducer implements StorVisitorConfig.Producer, StorFilestorConfig.Producer {
    public static class Builder {
        public StorVisitorProducer build(ModelElement element) {
            ModelElement tuning = element.child("tuning");
            if (tuning == null) {
                return new StorVisitorProducer();
            }

            ModelElement visitors = tuning.child("visitors");
            if (visitors == null) {
                return new StorVisitorProducer();
            }

            return new StorVisitorProducer(visitors.integerAttribute("thread-count"),
                                           visitors.integerAttribute("max-queue-size"),
                                           visitors.childAsInteger("max-concurrent.fixed"),
                                           visitors.childAsInteger("max-concurrent.variable"));
        }
    }

    Integer threadCount;
    Integer maxQueueSize;
    Integer maxConcurrentFixed;
    Integer maxConcurrentVariable;

    public StorVisitorProducer() {}

    StorVisitorProducer(Integer threadCount, Integer maxQueueSize, Integer maxConcurrentFixed, Integer maxConcurrentVariable) {
        this.threadCount = threadCount;
        this.maxQueueSize = maxQueueSize;
        this.maxConcurrentFixed = maxConcurrentFixed;
        this.maxConcurrentVariable = maxConcurrentVariable;
    }

    @Override
    public void getConfig(StorFilestorConfig.Builder builder) {
        if (threadCount != null) {
            builder.num_visitor_threads(threadCount);
        }
    }

    @Override
    public void getConfig(StorVisitorConfig.Builder builder) {
        if (threadCount != null) {
            builder.visitorthreads(threadCount);
        }
        if (maxQueueSize != null) {
            builder.maxvisitorqueuesize(maxQueueSize);
        }
        if (maxConcurrentFixed != null) {
            builder.maxconcurrentvisitors_fixed(maxConcurrentFixed);
        }
        if (maxConcurrentVariable != null) {
            builder.maxconcurrentvisitors_variable(maxConcurrentVariable);
        }
    }
}
