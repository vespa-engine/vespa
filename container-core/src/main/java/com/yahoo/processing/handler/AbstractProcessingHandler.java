// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.processing.handler;

import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.ChainedComponent;
import com.yahoo.component.chain.model.ChainsModelBuilder;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.jdisc.ContentChannelOutputStream;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.processing.Processor;
import com.yahoo.processing.Request;
import com.yahoo.processing.Response;
import com.yahoo.processing.execution.Execution;
import com.yahoo.processing.execution.ResponseReceiver;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.processing.rendering.AsynchronousSectionedRenderer;
import com.yahoo.processing.rendering.ProcessingRenderer;
import com.yahoo.processing.rendering.Renderer;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.processing.request.Properties;

import java.util.Map;
import java.util.concurrent.Executor;

import static com.yahoo.component.chain.ChainsConfigurer.prepareChainRegistry;

/**
 * Superclass of handlers invoking some kind of processing chain.
 * <p>
 * COMPONENT: The type of the processing components of which this executes a chain
 *
 * @author bratseth
 * @author Tony Vaagenes
 * @author Steinar Knutsen
 */
public abstract class AbstractProcessingHandler<COMPONENT extends Processor> extends LoggingRequestHandler {

    private final static CompoundName freezeListenerKey =new CompoundName("processing.freezeListener");

    public final static String DEFAULT_RENDERER_ID = "default";

    private final Executor renderingExecutor;

    private ChainRegistry<COMPONENT> chainRegistry;

    private final ComponentRegistry<Renderer> renderers;

    private final Renderer defaultRenderer;

    public AbstractProcessingHandler(ChainRegistry<COMPONENT> chainRegistry,
                                     ComponentRegistry<Renderer> renderers,
                                     Executor executor,
                                     AccessLog ignored,
                                     Metric metric) {
        super(executor, metric, true);
        renderingExecutor = executor;
        this.chainRegistry = chainRegistry;
        this.renderers = renderers;

        // Default is the one with id "default", or the ProcessingRenderer if there is no such renderer
        Renderer defaultRenderer = renderers.getComponent(ComponentSpecification.fromString(DEFAULT_RENDERER_ID));
        if (defaultRenderer == null) {
            defaultRenderer =  new ProcessingRenderer();
            renderers.register(ComponentId.fromString(DEFAULT_RENDERER_ID), defaultRenderer);
        }
        this.defaultRenderer = defaultRenderer;
    }

    public AbstractProcessingHandler(ChainRegistry<COMPONENT> chainRegistry,
                                     ComponentRegistry<Renderer> renderers,
                                     Executor executor,
                                     AccessLog ignored) {
        this(chainRegistry, renderers, executor, ignored, null);
    }

    public AbstractProcessingHandler(ChainsConfig processingChainsConfig,
                                     ComponentRegistry <COMPONENT> chainedComponents,
                                     ComponentRegistry<Renderer> renderers,
                                     Executor executor,
                                     AccessLog ignored) {
        this(processingChainsConfig, chainedComponents, renderers, executor, ignored, null);
    }

    @Inject
    public AbstractProcessingHandler(ChainsConfig processingChainsConfig,
                                     ComponentRegistry<COMPONENT> chainedComponents,
                                     ComponentRegistry<Renderer> renderers,
                                     Executor executor,
                                     AccessLog ignored,
                                     Metric metric) {
        this(createChainRegistry(processingChainsConfig, chainedComponents), renderers, executor, ignored, metric);
    }

    /** Throws UnsupportedOperationException: Call handle(request, channel instead) */
    @Override
    public HttpResponse handle(HttpRequest request) {
        throw new UnsupportedOperationException("Call handle(request, channel) instead");
    }

    @Override
    @SuppressWarnings("unchecked")
    public HttpResponse handle(HttpRequest request, ContentChannel channel) {
        com.yahoo.processing.Request processingRequest = new com.yahoo.processing.Request();
        populate("", request.propertyMap(), processingRequest.properties());
        populate("context", request.getJDiscRequest().context(), processingRequest.properties());
        processingRequest.properties().set(Request.JDISC_REQUEST, request);

        FreezeListener freezeListener = new FreezeListener(processingRequest, renderers, defaultRenderer, channel, renderingExecutor);
        processingRequest.properties().set(freezeListenerKey, freezeListener);

        Chain<COMPONENT> chain = chainRegistry.getComponent(resolveChainId(processingRequest.properties()));
        if (chain == null)
            throw new IllegalArgumentException("Chain '" + processingRequest.properties().get("chain") + "' not found");
        Execution execution = createExecution(chain, processingRequest);
        freezeListener.setExecution(execution);
        Response processingResponse = execution.process(processingRequest);

        return freezeListener.getHttpResponse(processingResponse);
    }

    public Execution createExecution(Chain<COMPONENT> chain, Request processingRequest) {
        int traceLevel = processingRequest.properties().getInteger("tracelevel", 0);
        return Execution.createRoot(chain, traceLevel, new Execution.Environment<>(chainRegistry));
    }

    public ChainRegistry<COMPONENT> getChainRegistry() { return chainRegistry; }

    public ComponentRegistry<Renderer> getRenderers() { return renderers; }

    /**
     * For internal use only
     */
    @SuppressWarnings("unchecked")
    public Renderer<Response> getRendererCopy(ComponentSpecification spec) {
        Renderer<Response> renderer = getRenderers().getComponent(spec);
        if (renderer == null) throw new IllegalArgumentException("No renderer with spec: " + spec);
        return perRenderingCopy(renderer);
    }

    private static Renderer<Response> perRenderingCopy(Renderer<Response> renderer) {
        Renderer<Response> copy = renderer.clone();
        copy.init();
        return copy;
    }

    private static Renderer selectRenderer(com.yahoo.processing.Request processingRequest,
                                           ComponentRegistry<Renderer> renderers, Renderer defaultRenderer) {
        Renderer renderer = null;
        // TODO: Support setting a particular renderer instead of just selecting by name?
        String rendererId = processingRequest.properties().getString("format");
        if (rendererId != null && !"".equals(rendererId)) {
            renderer = renderers.getComponent(ComponentSpecification.fromString(rendererId));
            if (renderer == null)
                processingRequest.errors().add(new ErrorMessage("Could not find renderer","Requested '" + rendererId +
                                                                "', has " + renderers.allComponents()));
        }
        if (renderer == null)
            renderer = defaultRenderer;
        return renderer;
    }

    private static <COMPONENT extends ChainedComponent> ChainRegistry<COMPONENT> createChainRegistry(
            ChainsConfig processingChainsConfig,
            ComponentRegistry<COMPONENT> availableComponents) {

        ChainRegistry<COMPONENT> chainRegistry = new ChainRegistry<>();
        prepareChainRegistry(chainRegistry, ChainsModelBuilder.buildFromConfig(processingChainsConfig), availableComponents);
        chainRegistry.freeze();
        return chainRegistry;
    }

    private String resolveChainId(Properties properties) {
        return properties.getString(Request.CHAIN,"default");
    }

    private void populate(String prefixName,Map<String,?> parameters,Properties properties) {
        CompoundName prefix = new CompoundName(prefixName);
        for (Map.Entry<String,?> entry : parameters.entrySet())
            properties.set(prefix.append(entry.getKey()),entry.getValue());
    }

    private static class FreezeListener implements Runnable, ResponseReceiver {

        /** Used to create the renderer */
        private final com.yahoo.processing.Request request;
        private final ComponentRegistry<Renderer> renderers;
        private final Renderer defaultRenderer;
        private final ContentChannel channel;
        private final Executor renderingExecutor;

        /** Used to render */
        private Execution execution;
        private Response response;

        /** The renderer used in this, or null if not created yet */
        private Renderer<Response> renderer = null;

        public FreezeListener(com.yahoo.processing.Request request, ComponentRegistry<Renderer> renderers,
                              Renderer defaultRenderer, ContentChannel channel, Executor renderingExecutor) {
            this.request = request;
            this.renderers = renderers;
            this.defaultRenderer = defaultRenderer;
            this.channel = channel;
            this.renderingExecutor = renderingExecutor;
        }

        /** Expected to be called once before run is called */
        @Override
        public void setResponse(Response response) { this.response = response; }

        /** Expected to be called once before run is called */
        public void setExecution(Execution execution) { this.execution = execution; }

        /** Returns and lazily creates the renderer of this. May be called even if run is never called. */
        public Renderer getRenderer() {
            if (renderer == null)
                renderer = perRenderingCopy(selectRenderer(request, renderers, defaultRenderer));
            return renderer;
        }

        /** Returns and lazily creates the http response of this. May be called even if run is never called. */
        private HttpResponse getHttpResponse(Response processingResponse) {
            int status = 200; // true status is determined asynchronously in ProcessingResponse.complete()
            return new ProcessingResponse(status, request, processingResponse, getRenderer(), renderingExecutor, execution);
        }

        @Override
        public void run() {
            if (execution == null || response == null)
                throw new NullPointerException("Uninitialized freeze listener");

            if (channel instanceof LazyContentChannel)
                ((LazyContentChannel)channel).setHttpResponse(getHttpResponse(response));

            // Render if we have a renderer capable of it
            if (getRenderer() instanceof AsynchronousSectionedRenderer) {
                ((AsynchronousSectionedRenderer) getRenderer()).renderBeforeHandover(new ContentChannelOutputStream(channel), response, execution, request);
            }
        }

    }

}
