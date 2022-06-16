// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.searchchain;

import com.yahoo.component.annotation.Inject;
import com.yahoo.api.annotations.Beta;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.ChainsConfigurer;
import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.component.chain.model.ChainsModelBuilder;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.language.Linguistics;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.prelude.IndexModel;
import com.yahoo.language.process.SpecialTokenRegistry;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.search.Searcher;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.schema.SchemaInfo;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.vespa.configdefinition.SpecialtokensConfig;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
    private final SchemaInfo schemaInfo;
    private final SpecialTokenRegistry specialTokens;
    private final Linguistics linguistics;
    private final ThreadPoolExecutor renderingExecutor;
    private final RendererRegistry rendererRegistry;
    private final Executor executor;

    // TODO: Fix tests depending on HandlersConfigurerTestWrapper so that this constructor can be removed
    @Beta
    @Inject
    public ExecutionFactory(ChainsConfig chainsConfig,
                            IndexInfoConfig indexInfo,
                            SchemaInfoConfig schemaInfo,
                            QrSearchersConfig clusters,
                            ComponentRegistry<Searcher> searchers,
                            SpecialtokensConfig specialTokens,
                            Linguistics linguistics,
                            ComponentRegistry<Renderer> renderers,
                            Executor executor) {
        this(chainsConfig,
             indexInfo,
             new SchemaInfo(indexInfo, schemaInfo, clusters),
             clusters,
             searchers,
             specialTokens,
             linguistics,
             renderers,
             executor);
    }

    public ExecutionFactory(ChainsConfig chainsConfig,
                            IndexInfoConfig indexInfo,
                            SchemaInfo schemaInfo,
                            QrSearchersConfig clusters,
                            ComponentRegistry<Searcher> searchers,
                            SpecialtokensConfig specialTokens,
                            Linguistics linguistics,
                            ComponentRegistry<Renderer> renderers,
                            Executor executor) {
        this.searchChainRegistry = createSearchChainRegistry(searchers, chainsConfig);
        this.indexFacts = new IndexFacts(new IndexModel(indexInfo, clusters)).freeze();
        this.schemaInfo = schemaInfo;
        this.specialTokens = new SpecialTokenRegistry(specialTokens);
        this.linguistics = linguistics;
        this.renderingExecutor = createRenderingExecutor();
        this.rendererRegistry = new RendererRegistry(renderers.allComponents(), renderingExecutor);
        this.executor = executor != null ? executor : Executors.newSingleThreadExecutor();
    }

    private SearchChainRegistry createSearchChainRegistry(ComponentRegistry<Searcher> searchers,
                                                          ChainsConfig chainsConfig) {
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
                             new Execution.Context(searchChainRegistry, indexFacts, schemaInfo, specialTokens, rendererRegistry, linguistics, executor));
    }

    /**
     * Creates a new execution starting at a search chain.
     * An execution instance should be used once to execute a (tree of) search chains.
     */
    public Execution newExecution(String searchChainId) {
        return new Execution(searchChainRegistry().getChain(searchChainId),
                             new Execution.Context(searchChainRegistry, indexFacts, schemaInfo, specialTokens, rendererRegistry, linguistics, executor));
    }

    /** Returns the search chain registry used by this */
    public SearchChainRegistry searchChainRegistry() { return searchChainRegistry; }

    /** Returns the renderers known to this */
    public RendererRegistry rendererRegistry() { return rendererRegistry; }

    public SchemaInfo schemaInfo() { return schemaInfo; }

    @Override
    public void deconstruct() {
        rendererRegistry.deconstruct();
        renderingExecutor.shutdown();
        try {
            if ( ! renderingExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                renderingExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            renderingExecutor.shutdownNow();
        }
    }

    public static ExecutionFactory empty() {
        return new ExecutionFactory(new ChainsConfig.Builder().build(),
                                    new IndexInfoConfig.Builder().build(),
                                    SchemaInfo.empty(),
                                    new QrSearchersConfig.Builder().build(),
                                    new ComponentRegistry<>(),
                                    new SpecialtokensConfig.Builder().build(),
                                    new SimpleLinguistics(),
                                    new ComponentRegistry<>(),
                                    null);
    }

    private static ThreadPoolExecutor createRenderingExecutor() {
        int threadCount = Runtime.getRuntime().availableProcessors();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(threadCount, threadCount, 1L, TimeUnit.SECONDS,
                                                             new LinkedBlockingQueue<>(),
                                                             ThreadFactoryFactory.getThreadFactory("common-rendering"));
        executor.prestartAllCoreThreads();
        return executor;
    }

}
