// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchConfig.DistributionPolicy;
import com.yahoo.vespa.config.search.DispatchNodesConfig;
import com.yahoo.vespa.model.content.DispatchTuning;
import com.yahoo.vespa.model.content.Redundancy;
import com.yahoo.vespa.model.content.SearchCoverage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Henning Baldersheim
 */
public class IndexedSearchCluster extends SearchCluster {

    private Tuning tuning;
    private SearchCoverage searchCoverage;

    private final Redundancy.Provider redundancyProvider;

    private final List<SearchNode> searchNodes = new ArrayList<>();
    private final double dispatchWarmup;
    private final String summaryDecodePolicy;

    public IndexedSearchCluster(TreeConfigProducer<AnyConfigProducer> parent, String clusterName,
                                Redundancy.Provider redundancyProvider, ModelContext.FeatureFlags featureFlags) {
        super(parent, clusterName);
        this.redundancyProvider = redundancyProvider;
        dispatchWarmup = featureFlags.queryDispatchWarmup();
        summaryDecodePolicy = featureFlags.summaryDecodePolicy();
    }

    public void addSearcher(SearchNode searcher) {
        searchNodes.add(searcher);
    }

    public List<SearchNode> getSearchNodes() { return Collections.unmodifiableList(searchNodes); }
    public int getSearchNodeCount() { return searchNodes.size(); }
    public SearchNode getSearchNode(int index) { return searchNodes.get(index); }
    public void setTuning(Tuning tuning) {
        this.tuning = tuning;
    }
    public Tuning getTuning() { return tuning; }

    public void setSearchCoverage(SearchCoverage searchCoverage) {
        this.searchCoverage = searchCoverage;
    }

    private static DistributionPolicy.Enum toDistributionPolicy(DispatchTuning.DispatchPolicy tuning) {
        return switch (tuning) {
            case ADAPTIVE: yield DistributionPolicy.ADAPTIVE;
            case ROUNDROBIN: yield DistributionPolicy.ROUNDROBIN;
            case BEST_OF_RANDOM_2: yield DistributionPolicy.BEST_OF_RANDOM_2;
            case LATENCY_AMORTIZED_OVER_REQUESTS: yield DistributionPolicy.LATENCY_AMORTIZED_OVER_REQUESTS;
            case LATENCY_AMORTIZED_OVER_TIME: yield DistributionPolicy.LATENCY_AMORTIZED_OVER_TIME;
        };
    }

    public void getConfig(DispatchNodesConfig.Builder builder) {
        for (SearchNode node : getSearchNodes()) {
            DispatchNodesConfig.Node.Builder nodeBuilder = new DispatchNodesConfig.Node.Builder();
            nodeBuilder.key(node.getDistributionKey());
            nodeBuilder.group(node.getNodeSpec().groupIndex());
            nodeBuilder.host(node.getHostName());
            nodeBuilder.port(node.getRpcPort());
            builder.node(nodeBuilder);
        }
    }

    public void getConfig(DispatchConfig.Builder builder) {
        if (tuning.dispatch.getTopkProbability() != null)
            builder.topKProbability(tuning.dispatch.getTopkProbability());
        if (tuning.dispatch.getPrioritizeAvailability() != null)
            builder.prioritizeAvailability(tuning.dispatch.getPrioritizeAvailability());
        if (tuning.dispatch.getMinActiveDocsCoverage() != null)
            builder.minActivedocsPercentage(tuning.dispatch.getMinActiveDocsCoverage());
        if (tuning.dispatch.getDispatchPolicy() != null)
            builder.distributionPolicy(toDistributionPolicy(tuning.dispatch.getDispatchPolicy()));
        if (tuning.dispatch.getMaxHitsPerPartition() != null)
            builder.maxHitsPerNode(tuning.dispatch.getMaxHitsPerPartition());

        builder.redundancy(redundancyProvider.redundancy().finalRedundancy());
        if (searchCoverage != null) {
            if (searchCoverage.getMinimum() != null)
                builder.minSearchCoverage(searchCoverage.getMinimum() * 100.0);
            if (searchCoverage.getMinWaitAfterCoverageFactor() != null)
                builder.minWaitAfterCoverageFactor(searchCoverage.getMinWaitAfterCoverageFactor());
            if (searchCoverage.getMaxWaitAfterCoverageFactor() != null)
                builder.maxWaitAfterCoverageFactor(searchCoverage.getMaxWaitAfterCoverageFactor());
        }
        builder.warmuptime(dispatchWarmup);
        builder.summaryDecodePolicy(toSummaryDecoding(summaryDecodePolicy));
    }

    private DispatchConfig.SummaryDecodePolicy.Enum toSummaryDecoding(String summaryDecodeType) {
        return switch (summaryDecodeType.toLowerCase()) {
            case "eager" -> DispatchConfig.SummaryDecodePolicy.EAGER;
            case "ondemand","on-demand" -> DispatchConfig.SummaryDecodePolicy.Enum.ONDEMAND;
            default -> DispatchConfig.SummaryDecodePolicy.Enum.EAGER;
        };
    }

    @Override
    public String toString() {
        return "Indexing cluster '" + getClusterName() + "'";
    }

}
