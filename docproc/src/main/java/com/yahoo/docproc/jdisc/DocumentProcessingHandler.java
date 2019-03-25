// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.google.inject.Inject;
import com.yahoo.component.chain.Chain;
import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.docproc.DocprocConfig;
import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.docproc.AbstractConcreteDocumentFactory;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.DocprocService;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.jdisc.messagebus.MbusRequestContext;
import com.yahoo.docproc.proxy.SchemaMap;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.ThroughputLimitQueue;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.jdisc.MbusRequest;
import com.yahoo.processing.execution.chain.ChainRegistry;
import com.yahoo.statistics.Statistics;

import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.logging.Logger;

import static com.yahoo.component.chain.ChainsConfigurer.prepareChainRegistry;
import static com.yahoo.component.chain.model.ChainsModelBuilder.buildFromConfig;

/**
 * TODO: Javadoc
 *
 * @author Einar M R Rosenvinge
 */
public class DocumentProcessingHandler extends AbstractRequestHandler {

    private static Logger log = Logger.getLogger(DocumentProcessingHandler.class.getName());
    private final ComponentRegistry<DocprocService> docprocServiceRegistry;
    private final ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry;
    private final ChainRegistry<DocumentProcessor> chainRegistry = new ChainRegistry<>();
    private DocprocThreadPoolExecutor threadPool;
    private final ScheduledThreadPoolExecutor laterExecutor =
            new ScheduledThreadPoolExecutor(2, new DaemonThreadFactory("docproc-later-"));
    private ContainerDocumentConfig containerDocConfig;
    private final DocumentTypeManager documentTypeManager;

    public DocumentProcessingHandler(ComponentRegistry<DocprocService> docprocServiceRegistry,
                                     ComponentRegistry<DocumentProcessor> documentProcessorComponentRegistry,
                                     ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry,
                                     DocprocThreadPoolExecutor threadPool, DocumentTypeManager documentTypeManager,
                                     ChainsModel chainsModel, SchemaMap schemaMap, Statistics statistics,
                                     Metric metric,
                                     ContainerDocumentConfig containerDocConfig) {
        this.docprocServiceRegistry = docprocServiceRegistry;
        this.docFactoryRegistry = docFactoryRegistry;
        this.threadPool = threadPool;
        this.containerDocConfig = containerDocConfig;
        this.documentTypeManager = documentTypeManager;
        DocprocService.schemaMap = schemaMap;
        threadPool.prestartCoreThread();
        laterExecutor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        laterExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);

        if (chainsModel != null) {
            prepareChainRegistry(chainRegistry, chainsModel, documentProcessorComponentRegistry);

            for (Chain<DocumentProcessor> chain : chainRegistry.allComponents()) {
                log.config("Setting up call stack for chain " + chain.getId());
                DocprocService service =
                        new DocprocService(chain.getId(), convertToCallStack(chain, statistics, metric), documentTypeManager);
                service.setInService(true);
                docprocServiceRegistry.register(service.getId(), service);
            }
        }
    }

    public DocumentProcessingHandler(ComponentRegistry<DocprocService> docprocServiceRegistry,
                                     ComponentRegistry<DocumentProcessor> documentProcessorComponentRegistry,
                                     ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry,
                                     DocumentProcessingHandlerParameters params) {
        this(docprocServiceRegistry, documentProcessorComponentRegistry, docFactoryRegistry,
             new DocprocThreadPoolExecutor(params.getMaxNumThreads(),
                                           (params.getMaxQueueTimeMs() > 0)
                                               ? new ThroughputLimitQueue<>(params.getMaxQueueTimeMs())
                                               : (params.getMaxQueueTimeMs() < 0)
                                                   ? new SynchronousQueue<>()
                                                   : new PriorityBlockingQueue<>(), //Probably no need to bound this queue, see bug #4254537
                                           new DocprocThreadManager(params.getMaxConcurrentFactor(),
                                                                    params.getDocumentExpansionFactor(),
                                                                    params.getContainerCoreMemoryMb(),
                                                                    params.getStatisticsManager(),
                                                                    params.getMetric())),
             params.getDocumentTypeManager(), params.getChainsModel(), params.getSchemaMap(),
             params.getStatisticsManager(),
             params.getMetric(),
             params.getContainerDocConfig());
    }

    @Inject
    public DocumentProcessingHandler(ComponentRegistry<DocumentProcessor> documentProcessorComponentRegistry,
                                     ComponentRegistry<AbstractConcreteDocumentFactory> docFactoryRegistry,
                                     ChainsConfig chainsConfig,
                                     SchemamappingConfig mappingConfig,
                                     DocumentmanagerConfig docManConfig,
                                     DocprocConfig docprocConfig,
                                     ContainerMbusConfig containerMbusConfig,
                                     ContainerDocumentConfig containerDocConfig,
                                     Statistics manager,
                                     Metric metric) {
        this(new ComponentRegistry<>(),
             documentProcessorComponentRegistry, docFactoryRegistry, new DocumentProcessingHandlerParameters().setMaxNumThreads
                (docprocConfig.numthreads())
                     .setMaxConcurrentFactor(containerMbusConfig.maxConcurrentFactor())
                     .setDocumentExpansionFactor(containerMbusConfig.documentExpansionFactor())
                     .setContainerCoreMemoryMb(containerMbusConfig.containerCoreMemory())
                     .setMaxQueueTimeMs(docprocConfig.maxqueuetimems())
                     .setDocumentTypeManager(new DocumentTypeManager(docManConfig))
                     .setChainsModel(buildFromConfig(chainsConfig)).setSchemaMap(configureMapping(mappingConfig))
                     .setStatisticsManager(manager)
                     .setMetric(metric)
                     .setContainerDocumentConfig(containerDocConfig));
    }

    @Override
    protected void destroy() {
        threadPool.shutdown();  //calling shutdownNow() seems like a bit of an overkill
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


    private static CallStack convertToCallStack(Chain<DocumentProcessor> chain, Statistics statistics, Metric metric) {
        CallStack stack = new CallStack(chain.getId().stringValue(), statistics, metric);
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

        DocprocService service = docprocServiceRegistry.getComponent(requestContext.getServiceName());
        //No need to enqueue a task if the docproc chain is empty, just forward requestContext
        if (service == null) {
            log.log(LogLevel.ERROR, "DocprocService for session '" + requestContext.getServiceName() +
                                    "' not found, returning request '" + requestContext + "'.");
            requestContext.processingFailed(RequestContext.ErrorCode.ERROR_PROCESSING_FAILURE,
                                            "DocprocService " + requestContext.getServiceName() + " not found.");
            return null;
        } else if (service.getExecutor().getCallStack().size() == 0) {
            //call stack was empty, just forward message
            requestContext.skip();
            return null;
        }

        DocumentProcessingTask task = new DocumentProcessingTask(requestContext, this, service);
        submit(task);
        return null;
    }

    @SuppressWarnings("unchecked")
    void submit(DocumentProcessingTask task) {
        if (threadPool.isAboveLimit()) {
            task.queueFull();
        } else {
            try {
                threadPool.execute(task);
            } catch (RejectedExecutionException ree) {
                task.queueFull();
            }
        }
    }

    void submit(DocumentProcessingTask task, long delay) {
        LaterTimerTask timerTask = new LaterTimerTask(task, delay);
        laterExecutor.schedule(timerTask, delay, TimeUnit.MILLISECONDS);
    }

    private class LaterTimerTask extends TimerTask {
        private DocumentProcessingTask processingTask;
        private long delay;

        private LaterTimerTask(DocumentProcessingTask processingTask, long delay) {
            this.delay = delay;
            log.log(LogLevel.DEBUG, "Enqueueing in " + delay + " ms due to Progress.LATER: " + processingTask);
            this.processingTask = processingTask;
        }

        @Override
        public void run() {
            log.log(LogLevel.DEBUG, "Submitting after having waited " + delay + " ms in LATER queue: " + processingTask);
            submit(processingTask);
        }
    }

    public DocumentTypeManager getDocumentTypeManager() {
        return documentTypeManager;
    }

}
