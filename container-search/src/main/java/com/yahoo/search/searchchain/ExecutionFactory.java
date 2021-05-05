// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.ChainsConfigurer;
import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.component.chain.model.ChainsModelBuilder;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.prelude.query.parser.SpecialTokenRegistry;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig;

/**
 * Provides creation of fully configured query Execution instances.
 * Have an instance of this injected if you need to execute queries which are not initiated from
 * an external request.
 *
 * @author bratseth
 */
public class ExecutionFactory extends AbstractComponent {

    private final SearchChainRegistry searchChainRegistry;
    private final IndexFacts indexFacts;
    private final SpecialTokenRegistry specialTokens;
    private final Linguistics linguistics;
    private final RendererRegistry rendererRegistry;

    public ExecutionFactory(ChainsConfig chainsConfig,
                            IndexInfoConfig indexInfo,
                            QrSearchersConfig clusters,
                            ComponentRegistry<Searcher> searchers,
                            SpecialtokensConfig specialTokens,
                            Linguistics linguistics,
                            ComponentRegistry<Renderer> renderers) {
        this.searchChainRegistry = createSearchChainRegistry(searchers, chainsConfig);
        this.indexFacts = new IndexFacts(new IndexModel(indexInfo, clusters)).freeze();
        this.specialTokens = new SpecialTokenRegistry(specialTokens);
        this.linguistics = linguistics;
        this.rendererRegistry = new RendererRegistry(renderers.allComponents());
    }

    private SearchChainRegistry createSearchChainRegistry(ComponentRegistry<Searcher> searchers, ChainsConfig chainsConfig) {
        SearchChainRegistry searchChainRegistry = new SearchChainRegistry(searchers);
        ChainsModel chainsModel = ChainsModelBuilder.buildFromConfig(chainsConfig);
        ChainsConfigurer.prepareChainRegistry(searchChainRegistry, chainsModel, searchers);
        searchChainRegistry.freeze();
        return searchChainRegistry;
    }

    /**
     * Creates a new execution starting at a search chain.
     * An execution instance should be used once to execute a (tree of) search chains.
     */
    public Execution newExecution(Chain<? extends Searcher> searchChain) {
        return new Execution(searchChain,
                             new Execution.Context(searchChainRegistry, indexFacts, specialTokens, rendererRegistry, linguistics));
    }

    /**
     * Creates a new execution starting at a search chain.
     * An execution instance should be used once to execute a (tree of) search chains.
     */
    public Execution newExecution(String searchChainId) {
        return new Execution(searchChainRegistry().getChain(searchChainId),
                             new Execution.Context(searchChainRegistry, indexFacts, specialTokens, rendererRegistry, linguistics));
    }

    /** Returns the search chain registry used by this */
    public SearchChainRegistry searchChainRegistry() { return searchChainRegistry; }

    /** Returns the renderers known to this */
    public RendererRegistry rendererRegistry() { return rendererRegistry; }

    @Override
    public void deconstruct() {
        rendererRegistry.deconstruct();
    }

    public static ExecutionFactory empty() {
        return new ExecutionFactory(new ChainsConfig.Builder().build(),
                                    new IndexInfoConfig.Builder().build(),
                                    new QrSearchersConfig.Builder().build(),
                                    new ComponentRegistry<>(),
                                    new SpecialtokensConfig.Builder().build(),
                                    new SimpleLinguistics(),
                                    new ComponentRegistry<>());
    }

}
