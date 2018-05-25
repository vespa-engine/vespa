// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain.model;

import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel;
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel.TargetSpec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Defines the searcher models used in the vespa and native search chains, except for federation.
 *
 * @author tonytv
 */
@SuppressWarnings({"rawtypes", "deprecation", "unchecked"})
public class VespaSearchers {
    public static final Collection<ChainedComponentModel> vespaSearcherModels =
            toSearcherModels(
                    com.yahoo.prelude.querytransform.PhrasingSearcher.class,
                    com.yahoo.prelude.searcher.FieldCollapsingSearcher.class,
                    com.yahoo.search.yql.MinimalQueryInserter.class,
                    com.yahoo.search.yql.FieldFilter.class,
                    com.yahoo.prelude.searcher.JuniperSearcher.class,
                    com.yahoo.prelude.searcher.BlendingSearcher.class,
                    com.yahoo.prelude.searcher.PosSearcher.class,
                    com.yahoo.prelude.semantics.SemanticSearcher.class,
                    com.yahoo.search.grouping.GroupingQueryParser.class);


    public static final Collection<ChainedComponentModel> nativeSearcherModels;

    static {
        nativeSearcherModels = new LinkedHashSet<>();
        nativeSearcherModels.add(federationSearcherModel());
        nativeSearcherModels.addAll(toSearcherModels(com.yahoo.prelude.statistics.StatisticsSearcher.class));

        //ensure that searchers in the native search chain are not overridden by searchers in the vespa search chain,
        //and that all component ids in each chain are unique.
        assert(allComponentIdsDifferent(vespaSearcherModels, nativeSearcherModels));
    }

    private static boolean allComponentIdsDifferent(Collection<ChainedComponentModel> vespaSearcherModels,
                                                    Collection<ChainedComponentModel> nativeSearcherModels) {
        Set<ComponentId> componentIds = new LinkedHashSet<>();
        return
                allAdded(vespaSearcherModels, componentIds) &&
                allAdded(nativeSearcherModels, componentIds);

    }

    private static FederationSearcherModel federationSearcherModel() {
        return new FederationSearcherModel(new ComponentSpecification("federation"),
                                           Dependencies.emptyDependencies(),
                                           Collections.<TargetSpec>emptyList(), true);
    }

    private static boolean allAdded(Collection<ChainedComponentModel> searcherModels, Set<ComponentId> componentIds) {
        for (ChainedComponentModel model : searcherModels) {
            if (!componentIds.add(model.getComponentId()))
                return false;
        }
        return true;
    }

    private static Collection<ChainedComponentModel> toSearcherModels(Class<? extends Searcher>... searchers) {
        List<ChainedComponentModel> searcherModels = new ArrayList<>();
        for (Class c : searchers) {
            searcherModels.add(
                    new ChainedComponentModel(
                            BundleInstantiationSpecification.getInternalSearcherSpecificationFromStrings(c.getName(), null),
                            Dependencies.emptyDependencies()));
        }
        return searcherModels;
    }
}

