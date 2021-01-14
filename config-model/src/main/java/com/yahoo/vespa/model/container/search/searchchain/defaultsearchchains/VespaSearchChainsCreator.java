// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search.searchchain.defaultsearchchains;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Phase;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.search.searchchain.PhaseNames;
import com.yahoo.search.searchchain.model.VespaSearchers;
import com.yahoo.search.searchchain.model.federation.FederationSearcherModel;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.search.searchchain.*;

import java.util.*;

/**
 * Creates the search chains vespaPhases, vespa and native.
 *
 * @author Tony Vaagenes
 */
// TODO: refactor
public class VespaSearchChainsCreator {

    private static class PhasesCreator {

        private static Set<String> set(String successor) {
            return successor == null ? null : new LinkedHashSet<>(List.of(successor));
        }

        private static String lastElement(String[] phases) {
            return phases[phases.length - 1];
        }

        private static Phase createPhase(String phase, String before) {
            return new Phase(phase, set(before), null);
        }

        public static Collection<Phase> linearPhases(String... phases) {
            List<Phase> result = new ArrayList<>();

            for (int i=0; i < phases.length - 1; ++i) {
                result.add(
                        createPhase(phases[i], phases[i+1]));
            }

            if (phases.length > 0) {
                result.add(
                        createPhase(lastElement(phases), null));
            }

            return result;
        }
    }

    private static Set<ComponentSpecification> noSearcherReferences() {
        return Collections.emptySet();
    }

    private static Collection<Phase> noPhases() {
        return Collections.emptySet();
    }

    private static ChainSpecification.Inheritance inherits(ComponentId chainId) {
        Set<ComponentSpecification> inheritsSet = new LinkedHashSet<>();
        inheritsSet.add(chainId.toSpecification());
        return new ChainSpecification.Inheritance(inheritsSet, null);
    }

    static ChainSpecification.Inheritance inheritsVespaPhases() {
        return inherits(vespaPhasesSpecification().componentId);
    }

    private static void addInnerSearchers(SearchChain searchChain, Collection<ChainedComponentModel> searcherModels) {
        for (ChainedComponentModel searcherModel : searcherModels) {
            searchChain.addInnerComponent(createSearcher(searcherModel));
        }
    }

    private static Searcher<? extends ChainedComponentModel> createSearcher(ChainedComponentModel searcherModel) {
        if (searcherModel instanceof FederationSearcherModel) {
            return new FederationSearcher((FederationSearcherModel) searcherModel, Optional.<Component>empty());
        } else {
            return new Searcher<>(searcherModel);
        }
    }

    private static ChainSpecification nativeSearchChainSpecification() {
        return new ChainSpecification(new ComponentId("native"),
                                      inheritsVespaPhases(),
                                      noPhases(),
                                      noSearcherReferences());
    }

    private static ChainSpecification vespaSearchChainSpecification() {
        return new ChainSpecification(new ComponentId("vespa"),
                                      inherits(nativeSearchChainSpecification().componentId),
                                      noPhases(),
                                      noSearcherReferences());
    }

    private static ChainSpecification vespaPhasesSpecification() {
        return new ChainSpecification(new ComponentId("vespaPhases"),
                                      new ChainSpecification.Inheritance(null, null),
                                      PhasesCreator.linearPhases(
                                              PhaseNames.RAW_QUERY,
                                              PhaseNames.TRANSFORMED_QUERY,
                                              PhaseNames.BLENDED_RESULT,
                                              PhaseNames.UNBLENDED_RESULT,
                                              PhaseNames.BACKEND),
                                      noSearcherReferences());
    }

    private static SearchChain createVespaPhases() {
        return new SearchChain(vespaPhasesSpecification());
    }

    private static SearchChain createNative() {
        SearchChain nativeChain = new SearchChain(nativeSearchChainSpecification());
        addInnerSearchers(nativeChain, VespaSearchers.nativeSearcherModels);
        return nativeChain;
    }

    private static SearchChain createVespa() {
        SearchChain vespaChain = new SearchChain(vespaSearchChainSpecification());
        addInnerSearchers(vespaChain, VespaSearchers.vespaSearcherModels);
        return vespaChain;
    }

    public static void addVespaSearchChains(SearchChains searchChains) {
        searchChains.add(createVespaPhases());
        searchChains.add(createNative());
        searchChains.add(createVespa());
    }

}
