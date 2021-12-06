// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.handler.HttpSearchResponse;
import com.yahoo.search.handler.SearchHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * @author Einar M R Rosenvinge
 * @author gjoranv
*/
@Beta
public final class Search extends ProcessingBase<Query, Result, Searcher> {

    private final SearchHandler handler;

    Search(SearchHandler handler) {
        this.handler = handler;
    }

    @Override
    public ChainRegistry<Searcher> getChains() {
        return asChainRegistry();
    }

    @Override
    protected Result doProcess(Chain<Searcher> chain, Query request) {
        return handler.searchAndFill(request, chain);
    }

    @Override
    protected CompletableFuture<Boolean> doProcessAndRender(ComponentSpecification chainSpec,
                                                            Query request,
                                                            Renderer<Result> renderer,
                                                            ByteArrayOutputStream stream) throws IOException {
        Result result = process(chainSpec, request);
        return HttpSearchResponse.asyncRender(result, result.getQuery(), renderer, stream);
    }

    @Override
    protected Renderer<Result> doGetRenderer(ComponentSpecification spec) {
        return handler.getRendererCopy(spec);
    }

    // TODO: move to SearchHandler.getChainRegistry and deprecate SH.getSCReg?
    private ChainRegistry<Searcher> asChainRegistry() {
        ChainRegistry<Searcher> chains = new ChainRegistry<>();
        for (Chain<Searcher> chain : handler.getSearchChainRegistry().allComponents())
            chains.register(chain.getId(), chain);
        chains.freeze();
        return chains;
    }

}
