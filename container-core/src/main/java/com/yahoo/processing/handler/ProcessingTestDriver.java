// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.handler;

import com.google.common.annotations.Beta;
import com.yahoo.component.ComponentId;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.RequestLogHandler;
import com.yahoo.processing.Processor;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.processing.rendering.Renderer;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A helper for making processing requests and rendering their responses.
 * Create an instance of this to test making processing requests and get the response or response data.
 *
 * @author bratseth
 */
@Beta
public class ProcessingTestDriver extends RequestHandlerTestDriver {

    private final ProcessingHandler processingHandler;

    public ProcessingTestDriver(Collection<Chain<Processor>> chains) {
        this(chains, new ComponentRegistry<>());
    }
    public ProcessingTestDriver(String binding, Collection<Chain<Processor>> chains) {
        this(chains, new ComponentRegistry<>());
    }
    @SafeVarargs
    @SuppressWarnings("varargs")
    public ProcessingTestDriver(Chain<Processor> ... chains) {
        this(Arrays.asList(chains), new ComponentRegistry<>());
    }
    @SafeVarargs
    @SuppressWarnings("varargs")
    public ProcessingTestDriver(String binding, Chain<Processor> ... chains) {
        this(binding, Arrays.asList(chains), new ComponentRegistry<>());
    }
    public ProcessingTestDriver(Collection<Chain<Processor>> chains, ComponentRegistry<Renderer> renderers) {
        this(createProcessingHandler(chains, renderers, AccessLog.voidAccessLog()));
    }
    public ProcessingTestDriver(String binding, Collection<Chain<Processor>> chains, ComponentRegistry<Renderer> renderers) {
        this(binding, createProcessingHandler(chains, renderers, AccessLog.voidAccessLog()));
    }
    public ProcessingTestDriver(ProcessingHandler processingHandler) {
        super(processingHandler);
        this.processingHandler = processingHandler;
    }
    public ProcessingTestDriver(String binding, ProcessingHandler processingHandler) {
        super(binding, processingHandler);
        this.processingHandler = processingHandler;
    }

    public ProcessingTestDriver(Chain<Processor> chain, RequestLogHandler accessLogInterface) {
        this(createProcessingHandler(
                Collections.singleton(chain),
                new ComponentRegistry<>(),
                createAccessLog(accessLogInterface)));
    }

    private static AccessLog createAccessLog(RequestLogHandler accessLogInterface) {
        ComponentRegistry<RequestLogHandler> componentRegistry = new ComponentRegistry<>();
        componentRegistry.register(ComponentId.createAnonymousComponentId("access-log"), accessLogInterface);
        componentRegistry.freeze();

        return new AccessLog(componentRegistry);
    }

    private static ProcessingHandler createProcessingHandler(Collection<Chain<Processor>> chains,
                                                             ComponentRegistry<Renderer> renderers,
                                                             AccessLog accessLog) {
        Executor executor = Executors.newSingleThreadExecutor();

        ChainRegistry<Processor> registry = new ChainRegistry<>();
        for (Chain<Processor> chain : chains)
            registry.register(chain.getId(), chain);
        return new ProcessingHandler(registry, renderers, executor, accessLog);
    }

    /** Returns the processing handler of this */
    public ProcessingHandler processingHandler() { return processingHandler; }

}
