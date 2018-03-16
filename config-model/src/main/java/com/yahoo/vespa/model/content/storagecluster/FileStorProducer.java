// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

/**
 * Serves stor-filestor for storage clusters.
 */
public class FileStorProducer implements StorFilestorConfig.Producer {

    public static class Builder {
        protected FileStorProducer build(ContentCluster parent, ModelElement clusterElem) {
            return new FileStorProducer(parent, getThreads(clusterElem));
        }

       private Integer getThreads(ModelElement clusterElem) {
           ModelElement tuning = clusterElem.getChild("tuning");
           if (tuning == null) {
               return null;
           }
           ModelElement threads = tuning.getChild("persistence-threads");
           if (threads == null) {
               return null;
           }

           Integer count = threads.getIntegerAttribute("count");
           if (count != null) {
               return count;
           }

           // Backward compatible fallback
           int numThreads = 0;
           for (ModelElement thread : threads.subElements("thread")) {
               count = thread.getIntegerAttribute("count");
               numThreads += (count == null) ? 1 : count;
           }

           return numThreads;
       }
    }

    private Integer numThreads;
    private ContentCluster cluster;

    public FileStorProducer(ContentCluster parent, Integer numThreads) {
        this.numThreads = numThreads;
        this.cluster = parent;
    }

    @Override
    public void getConfig(StorFilestorConfig.Builder builder) {
        if (numThreads != null) {
            builder.num_threads(numThreads);
        }
        builder.enable_multibit_split_optimalization(cluster.getPersistence().enableMultiLevelSplitting());
    }

}
