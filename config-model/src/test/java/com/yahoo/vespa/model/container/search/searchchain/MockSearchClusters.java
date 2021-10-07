// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain;

import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import java.util.HashMap;
import java.util.Map;

public class MockSearchClusters {
    private static class MockSearchCluster extends AbstractSearchCluster {
        public MockSearchCluster(AbstractConfigProducerRoot root, String clusterName, int clusterIndex, boolean isStreaming) {
            super(root, clusterName, clusterIndex);
            streaming = isStreaming;
        }
        private final boolean streaming;

        @Override
        public int getRowBits() {
            return 0;
        }

        @Override
        protected AbstractSearchCluster.IndexingMode getIndexingMode() { return streaming ? AbstractSearchCluster.IndexingMode.STREAMING : AbstractSearchCluster.IndexingMode.REALTIME; }

        @Override
        public void getConfig(DocumentdbInfoConfig.Builder builder) {
        }

        @Override
        public void getConfig(IndexInfoConfig.Builder builder) {
        }

        @Override
        public void getConfig(IlscriptsConfig.Builder builder) {
        }

        @Override
        public void getConfig(AttributesConfig.Builder builder) {
        }

        @Override
        public void getConfig(RankProfilesConfig.Builder builder) {
        }

    }

    public static AbstractSearchCluster mockSearchCluster(AbstractConfigProducerRoot root, String clusterName, int clusterIndex, boolean isStreaming) {

        return new MockSearchCluster(root, clusterName, clusterIndex, isStreaming);
    }

    public static Map<String, AbstractSearchCluster> twoMockClusterSpecsByName(AbstractConfigProducerRoot root) {
        Map<String, AbstractSearchCluster> result = new HashMap<>();
        result.put("cluster1", mockSearchCluster(root, "cluster1", 1, false));
        result.put("cluster2", mockSearchCluster(root, "cluster2", 2, true));
        return result;
    }
}
