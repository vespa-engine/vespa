// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.processing.rendering.Renderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author gjoranv
 */
@Beta
public abstract class ProcessingBase<REQUEST extends Request, RESPONSE extends Response, PROCESSOR extends Processor> {

    /** Returns a registry of configured chains */
    public abstract ChainRegistry<PROCESSOR> getChains();

    /**
     * Processes the given request with the given chain, and returns the response.
     *
     * @param chain the specification of the chain to execute
     * @param request the request to process
     * @return a response
     */
    public final RESPONSE process(ComponentSpecification chain, REQUEST request) {
        Chain<PROCESSOR> processingChain = getChain(chain);
        return doProcess(processingChain, request);
    }

    protected abstract RESPONSE doProcess(Chain<PROCESSOR> chain, REQUEST request);

    public final byte[] processAndRender(ComponentSpecification chainSpec,
                                         ComponentSpecification rendererSpec,
                                         REQUEST request) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Renderer<RESPONSE> renderer = getRenderer(rendererSpec);
        CompletableFuture<Boolean> renderTask = doProcessAndRender(chainSpec, request, renderer, stream);

        awaitFuture(renderTask);
        return stream.toByteArray();
    }

    private void awaitFuture(CompletableFuture<Boolean> renderTask) {
        try {
            renderTask.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ApplicationException(e);
        }
    }

    protected abstract CompletableFuture<Boolean> doProcessAndRender(ComponentSpecification chainSpec,
                                                                     REQUEST request,
                                                                     Renderer<RESPONSE> renderer,
                                                                     ByteArrayOutputStream stream) throws IOException ;

    protected Chain<PROCESSOR> getChain(ComponentSpecification chainSpec) {
        Chain<PROCESSOR> chain = getChains().getComponent(chainSpec);
        if (chain == null) {
            throw new IllegalArgumentException("No such chain: " + chainSpec);
        }
        return chain;
    }

    protected final Renderer<RESPONSE> getRenderer(ComponentSpecification spec) {
        return doGetRenderer(spec);
    }

    // TODO: This would not be necessary if Search/ProcHandler implemented a common interface
    protected abstract Renderer<RESPONSE> doGetRenderer(ComponentSpecification spec);

}
