// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.handler;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Metric;
import com.yahoo.processing.Processor;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.processing.rendering.Renderer;

import java.util.concurrent.Executor;

/**
 * A request handler which invokes a processing chain to produce the response.
 *
 * @author Tony Vaagenes
 */
public class ProcessingHandler extends AbstractProcessingHandler<Processor> {

    public ProcessingHandler(ChainRegistry<Processor> chainRegistry,
                             ComponentRegistry<Renderer> renderers,
                             Executor executor,
                             AccessLog accessLog,
                             Metric metric) {
        super(chainRegistry, renderers, executor, accessLog, metric);
    }

    public ProcessingHandler(ChainRegistry<Processor> chainRegistry,
                             ComponentRegistry<Renderer> renderers,
                             Executor executor,
                             AccessLog accessLog) {
        super(chainRegistry, renderers, executor, accessLog);
    }

    public ProcessingHandler(ChainsConfig processingChainsConfig,
                             ComponentRegistry<Processor> chainedComponents,
                             ComponentRegistry<Renderer> renderers,
                             Executor executor,
                             AccessLog accessLog) {
        super(processingChainsConfig, chainedComponents, renderers, executor, accessLog);
    }

    @Inject
    public ProcessingHandler(ChainsConfig processingChainsConfig,
                             ComponentRegistry<Processor> chainedComponents,
                             ComponentRegistry<Renderer> renderers,
                             Executor executor,
                             AccessLog accessLog,
                             Metric metric) {
        super(processingChainsConfig, chainedComponents, renderers, executor, accessLog, metric);
    }

}
