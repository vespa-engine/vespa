// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.model.federation;

import com.google.common.collect.ImmutableList;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.search.Searcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.jcip.annotations.Immutable;

/**
 * Specifies how a local provider is to be set up.
 *
 * @author Tony Vaagenes
 */
@Immutable
public class LocalProviderSpec {

    public static final Collection<ChainedComponentModel> searcherModels =
                    toSearcherModels(
                            com.yahoo.prelude.querytransform.CJKSearcher.class,
                            com.yahoo.search.querytransform.NGramSearcher.class,
                            com.yahoo.prelude.querytransform.LiteralBoostSearcher.class,
                            com.yahoo.prelude.querytransform.NormalizingSearcher.class,
                            com.yahoo.prelude.querytransform.StemmingSearcher.class,
                            com.yahoo.search.querytransform.VespaLowercasingSearcher.class,
                            com.yahoo.search.querytransform.DefaultPositionSearcher.class,
                            com.yahoo.search.querytransform.RangeQueryOptimizer.class,
                            com.yahoo.search.querytransform.SortingDegrader.class,
                            com.yahoo.prelude.searcher.ValidateSortingSearcher.class,
                            com.yahoo.prelude.cluster.ClusterSearcher.class,
                            com.yahoo.search.grouping.GroupingValidator.class,
                            com.yahoo.search.grouping.vespa.GroupingExecutor.class,
                            com.yahoo.prelude.querytransform.RecallSearcher.class,
                            com.yahoo.search.querytransform.WandSearcher.class,
                            com.yahoo.search.querytransform.BooleanSearcher.class,
                            com.yahoo.prelude.searcher.ValidatePredicateSearcher.class,
                            com.yahoo.search.searchers.ValidateNearestNeighborSearcher.class,
                            com.yahoo.search.searchers.ValidateMatchPhaseSearcher.class,
                            com.yahoo.search.yql.FieldFiller.class,
                            com.yahoo.search.searchers.InputCheckingSearcher.class,
                            com.yahoo.search.searchers.ContainerLatencySearcher.class);

    public final String clusterName;

    // TODO: make this final
    public Integer cacheSize;

    public LocalProviderSpec(String clusterName, Integer cacheSize) {
        this.clusterName = clusterName;
        this.cacheSize = cacheSize;

        if (clusterName == null)
            throw new IllegalArgumentException("Missing cluster name.");
    }

    public static boolean includesType(String type) {
        return "local".equals(type);
    }

    @SafeVarargs
    private static Collection<ChainedComponentModel> toSearcherModels(Class<? extends Searcher>... searchers) {
        List<ChainedComponentModel> searcherModels = new ArrayList<>();

        for (Class<? extends Searcher> c : searchers) {
            searcherModels.add(
                    new ChainedComponentModel(
                            BundleInstantiationSpecification.getInternalSearcherSpecificationFromStrings(
                                    c.getName(),
                                    null),
                            Dependencies.emptyDependencies()));
        }

        return ImmutableList.copyOf(searcherModels);
    }
}
