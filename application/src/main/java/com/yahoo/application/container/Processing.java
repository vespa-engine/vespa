// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.processing.handler.ProcessingHandler;
import com.yahoo.processing.rendering.Renderer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * @author Einar M R Rosenvinge
 * @author gjoranv
*/
@Beta
public final class Processing extends ProcessingBase<Request, Response, Processor> {

    private final ProcessingHandler handler;

    Processing(ProcessingHandler handler) {
        this.handler = handler;
    }

    @Override
    public ChainRegistry<Processor> getChains() {
        return handler.getChainRegistry();
    }

    @Override
    protected Response doProcess(Chain<Processor> chain, Request request) {
        Execution execution = handler.createExecution(chain, request);
        return execution.process(request);
    }

    @Override
    protected CompletableFuture<Boolean> doProcessAndRender(ComponentSpecification chainSpec,
                                                            Request request,
                                                            Renderer<Response> renderer,
                                                            ByteArrayOutputStream stream) throws IOException {
        Execution execution = handler.createExecution(getChain(chainSpec), request);
        Response response = execution.process(request);

        return renderer.renderResponse(stream, response, execution, request);
    }

    @Override
    protected Renderer<Response> doGetRenderer(ComponentSpecification spec) {
        return handler.getRendererCopy(spec);
    }

}
