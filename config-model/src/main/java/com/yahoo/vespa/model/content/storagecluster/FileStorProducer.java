// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.storagecluster;

import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.content.PriorityMapping;
import com.yahoo.vespa.model.content.cluster.ContentCluster;

import java.util.ArrayList;
import java.util.List;

/**
 * Serves stor-filestor for storage clusters.
 */
public class FileStorProducer implements StorFilestorConfig.Producer {

    public static class Builder {
        protected FileStorProducer build(ContentCluster parent, ModelElement clusterElem) {
            return new FileStorProducer(parent, getThreads(clusterElem));
        }

       private List<StorFilestorConfig.Threads.Builder> getThreads(ModelElement clusterElem) {
           ModelElement tuning = clusterElem.getChild("tuning");
           if (tuning == null) {
               return null;
           }
           ModelElement threads = tuning.getChild("persistence-threads");
           if (threads == null) {
               return null;
           }

           List<StorFilestorConfig.Threads.Builder> retVal = new ArrayList<>();

           PriorityMapping mapping = new PriorityMapping(clusterElem);

           for (ModelElement thread : threads.subElements("thread")) {
               String priorityName = thread.getStringAttribute("lowest-priority");
               if (priorityName == null) {
                   priorityName = "LOWEST";
               }

               Integer count = thread.getIntegerAttribute("count");
               if (count == null) {
                   count = 1;
               }

               for (int i = 0; i < count; ++i) {
                   retVal.add(new StorFilestorConfig.Threads.Builder().lowestpri(mapping.getPriorityMapping(priorityName)));
               }
           }

           return retVal;
       }
    }

    private List<StorFilestorConfig.Threads.Builder> threads;
    private ContentCluster cluster;

    public FileStorProducer(ContentCluster parent, List<StorFilestorConfig.Threads.Builder> threads) {
        this.threads = threads;
        this.cluster = parent;
    }

    @Override
    public void getConfig(StorFilestorConfig.Builder builder) {
        if (threads != null) {
            for (StorFilestorConfig.Threads.Builder t : threads) {
                builder.threads.add(t);
            }
        }
        builder.enable_multibit_split_optimalization(cluster.getPersistence().enableMultiLevelSplitting());
    }

}
