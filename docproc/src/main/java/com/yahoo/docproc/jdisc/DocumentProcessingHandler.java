// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.docproc.DocprocConfig;
import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.docproc.AbstractConcreteDocumentFactory;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.impl.DocprocService;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.jdisc.messagebus.MbusRequestContext;
import com.yahoo.docproc.proxy.SchemaMap;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.messagebus.jdisc.MbusRequest;
import com.yahoo.processing.execution.chain.ChainRegistry;

import java.util.TimerTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

import static com.yahoo.component.chain.ChainsConfigurer.prepareChainRegistry;
import static com.yahoo.component.chain.model.ChainsModelBuilder.buildFromConfig;

/**
 * TODO: Javadoc
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentProcessingHandler extends AbstractRequestHandler {

    private static final Logger log = Logger.getLogger(DocumentProcessingHandler.class.getName());
    private final ComponentRegistry<DocprocService> docprocServiceRegistry;
    private final ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry;
    private final ChainRegistry<DocumentProcessor> chainRegistry = new ChainRegistry<>();
    private final ScheduledThreadPoolExecutor laterExecutor =
            new ScheduledThreadPoolExecutor(2, new DaemonThreadFactory("docproc-later-"));
    private final ContainerDocumentConfig containerDocConfig;
    private final DocumentTypeManager documentTypeManager;

    private DocumentProcessingHandler(ComponentRegistry<DocprocService> docprocServiceRegistry,
                                      ComponentRegistry<DocumentProcessor> documentProcessorComponentRegistry,
                                      ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry,
                                      int numThreads,
                                      DocumentTypeManager documentTypeManager,
                                      ChainsModel chainsModel, SchemaMap schemaMap,
                                      Metric metric,
                                      ContainerDocumentConfig containerDocConfig) {
        this.docprocServiceRegistry = docprocServiceRegistry;
        this.docFactoryRegistry = docFactoryRegistry;
        this.containerDocConfig = containerDocConfig;
        this.documentTypeManager = documentTypeManager;
        DocprocService.schemaMap = schemaMap;
        laterExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        laterExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

        if (chainsModel != null) {
            prepareChainRegistry(chainRegistry, chainsModel, documentProcessorComponentRegistry);

            for (Chain<DocumentProcessor> chain : chainRegistry.allComponents()) {
                log.config("Setting up call stack for chain " + chain.getId());
                DocprocService service = new DocprocService(chain.getId(), convertToCallStack(chain, metric), documentTypeManager, computeNumThreads(numThreads));
                service.setInService(true);
                docprocServiceRegistry.register(service.getId(), service);
            }
        }
    }

    private static int computeNumThreads(int maxThreads) {
        return (maxThreads > 0) ? maxThreads : Runtime.getRuntime().availableProcessors();
    }

    DocumentProcessingHandler(ComponentRegistry<DocprocService> docprocServiceRegistry,
                              ComponentRegistry<DocumentProcessor> documentProcessorComponentRegistry,
                              ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry,
                              DocumentProcessingHandlerParameters params) {
        this(docprocServiceRegistry, documentProcessorComponentRegistry, docFactoryRegistry,
             params.getMaxNumThreads(),
             params.getDocumentTypeManager(), params.getChainsModel(), params.getSchemaMap(),
             params.getMetric(),
             params.getContainerDocConfig());
    }

    @Inject
    public DocumentProcessingHandler(ComponentRegistry<DocumentProcessor> documentProcessorComponentRegistry,
                                     ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry,
                                     ChainsConfig chainsConfig,
                                     SchemamappingConfig mappingConfig,
                                     DocumentTypeManager documentTypeManager,
                                     DocprocConfig docprocConfig,
                                     ContainerDocumentConfig containerDocConfig,
                                     Metric metric) {
        this(new ComponentRegistry<>(),
             documentProcessorComponentRegistry, docFactoryRegistry,
                new DocumentProcessingHandlerParameters()
                     .setMaxNumThreads(docprocConfig.numthreads())
                     .setDocumentTypeManager(documentTypeManager)
                     .setChainsModel(buildFromConfig(chainsConfig)).setSchemaMap(configureMapping(mappingConfig))
                     .setMetric(metric)
                     .setContainerDocumentConfig(containerDocConfig));
        docprocServiceRegistry.freeze();
    }

    @Override
    protected void destroy() {
        laterExecutor.shutdown();
        docprocServiceRegistry.allComponents().forEach(docprocService -> docprocService.deconstruct());
    }

    public ComponentRegistry<DocprocService> getDocprocServiceRegistry() {
        return docprocServiceRegistry;
    }

    public ChainRegistry<DocumentProcessor> getChains() {
        return chainRegistry;
    }

    private static SchemaMap configureMapping(SchemamappingConfig mappingConfig) {
        SchemaMap map = new SchemaMap();
        map.configure(mappingConfig);
        return map;
    }


    private static CallStack convertToCallStack(Chain<DocumentProcessor> chain, Metric metric) {
        CallStack stack = new CallStack(chain.getId().stringValue(), metric);
        for (DocumentProcessor processor : chain.components()) {
            processor.getFieldMap().putAll(DocprocService.schemaMap.chainMap(chain.getId().stringValue(), processor.getId().stringValue()));
            stack.addLast(processor);
        }
        return stack;
    }

    @Override
    public ContentChannel handleRequest(Request request, ResponseHandler handler) {
        RequestContext requestContext;
        if (request instanceof MbusRequest) {
            requestContext = new MbusRequestContext((MbusRequest) request, handler, docprocServiceRegistry, docFactoryRegistry, containerDocConfig);
        } else {
            //Other types can be added here in the future
            throw new IllegalArgumentException("Request type not supported: " + request);
        }

        if (!requestContext.isProcessable()) {
            requestContext.skip();
            return null;
        }

        String serviceName = requestContext.getServiceName();
        DocprocService service = docprocServiceRegistry.getComponent(serviceName);
        // No need to enqueue a task if the docproc chain is empty, just forward requestContext
        if (service == null) {
            log.log(Level.SEVERE, "DocprocService for session '" + serviceName +
                                    "' not found, returning request '" + requestContext + "'.");
            requestContext.processingFailed(RequestContext.ErrorCode.ERROR_PROCESSING_FAILURE,
                                            "DocprocService " + serviceName + " not found.");
            return null;
        } else if (service.getExecutor().getCallStack().size() == 0) {
            //call stack was empty, just forward message
            requestContext.skip();
            return null;
        }

        DocumentProcessingTask task = new DocumentProcessingTask(requestContext, this, service, service.getThreadPoolExecutor());
        task.submit();
        return null;
    }

    void submit(DocumentProcessingTask task, long delay) {
        LaterTimerTask timerTask = new LaterTimerTask(task, delay);
        laterExecutor.schedule(timerTask, delay, TimeUnit.MILLISECONDS);
    }

    private static class LaterTimerTask extends TimerTask {
        private final DocumentProcessingTask processingTask;
        private final long delay;

        private LaterTimerTask(DocumentProcessingTask processingTask, long delay) {
            this.delay = delay;
            log.log(Level.FINE, () -> "Enqueueing in " + delay + " ms due to Progress.LATER: " + processingTask);
            this.processingTask = processingTask;
        }

        @Override
        public void run() {
            log.log(Level.FINE, () -> "Submitting after having waited " + delay + " ms in LATER queue: " + processingTask);
            processingTask.submit();
        }
    }

    public DocumentTypeManager getDocumentTypeManager() {
        return documentTypeManager;
    }

}
