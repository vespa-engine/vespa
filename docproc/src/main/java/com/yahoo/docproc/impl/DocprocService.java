// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.impl;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.docproc.CallStack;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.docproc.proxy.SchemaMap;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentTypeManager;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>The document processing service.
 * Use this to set up a document processing chain and to
 * process documents using that chain. Note that there may
 * be multiple named instances of this service in the same
 * runtime. The default service is called "default" and is always present.</p>
 * 
 * <p>To create a server which receives documents from the network
 * and processes them, have a look at com.yahoo.docproc.server.Server.</p>
 *
 * <p>This class is thread safe.</p>
 *
 * @author bratseth
 */
public class DocprocService extends AbstractComponent {

    private static final Logger log = Logger.getLogger(DocprocService.class.getName());
    private volatile DocprocExecutor executor;

    /** The processings currently in progress at this service */
    private final LinkedBlockingQueue<Processing> queue;
    private final ThreadPoolExecutor threadPool;
    /** The current state of this service */
    private boolean inService = false;
    /** The current state of this service */
    private boolean acceptingNewProcessings = true;
    public static SchemaMap schemaMap = new SchemaMap();
    private DocumentTypeManager documentTypeManager = null;

    private DocprocService(ComponentId id, int numThreads) {
        super(id);
        queue = new LinkedBlockingQueue<>();
        threadPool = new ThreadPoolExecutor(numThreads,
                numThreads,
                0, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new DaemonThreadFactory("docproc-" + id.stringValue() + "-"));
    }

    public DocprocService(ComponentId id) {
        this(id, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Creates a new docproc service, which is set to be in service.
     *
     * @param id the component id of the new service.
     * @param stack the call stack to use.
     * @param mgr the document type manager to use.
     * @param numThreads to have in the thread pool
     */
    public DocprocService(ComponentId id, CallStack stack, DocumentTypeManager mgr, int numThreads) {
        this(id, numThreads);
        setCallStack(stack);
        setDocumentTypeManager(mgr);
        setInService(true);
    }

    /**
     * Creates a service with a name with an unbounded input queue. If the given name is null or the empty string,
     * it will become the name "default".
     * Testing only
     */
    public DocprocService(String name) {
        this(new ComponentId(name, null), 1);
    }

    @Override
    public void deconstruct() {
        threadPool.shutdown();
    }

    public DocumentTypeManager getDocumentTypeManager() {
        return documentTypeManager;
    }

    public void setDocumentTypeManager(DocumentTypeManager documentTypeManager) {
        this.documentTypeManager = documentTypeManager;
    }

    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Returns the DocprocExecutor of this DocprocService. This can be used to
     * synchronously process one Processing.
     *
     * @return the DocprocExecutor of this DocprocService, or null if a CallStack has not yet been set.
     */
    public DocprocExecutor getExecutor() {
        return executor;
    }

    public ThreadPoolExecutor getThreadPoolExecutor() {
        return threadPool;
    }

    private void setExecutor(DocprocExecutor executor) {
        this.executor = executor;
    }

    /**
     * Sets whether this should currently perform any processing.
     * New processings will be accepted also when this is out of service,
     * but no processing will happen when it is out of service.
     */
    public void setInService(boolean inService) {
        this.inService = inService;
    }

    /**
     * Returns true if this is currently processing incoming processings
     * (in service), or false if they are just queued up (out of service).
     * By default, this is out of service.
     */
    public boolean isInService() {
        return inService;
    }

    /**
     * Returns true if this service currently accepts new incoming processings via process(...).&nbsp;Default is true.
     *
     * @return true if accepting new incoming processings
     */
    public boolean isAcceptingNewProcessings() {
        return acceptingNewProcessings;
    }

    /**
     * Sets whether this service should accept new incoming processings via process(...).
     *
     * @param acceptingNewProcessings true if service should accept new incoming processings
     */
    public void setAcceptingNewProcessings(boolean acceptingNewProcessings) {
        this.acceptingNewProcessings = acceptingNewProcessings;
    }

    public String getName() {
        return getId().stringValue();
    }

    /**
     * Returns the processing chain of this service. This stack can not be modified.
     * To change the stack, set a new one.
     */
    // TODO: Enforce unmodifiability
    public CallStack getCallStack() {
        DocprocExecutor ex = getExecutor();
        return (ex == null) ? null : ex.getCallStack();
    }

    /**
     * Sets a new processing stack for this service. This will be the Prototype
     * for the call stacks of individual processings in this service
     */
    public void setCallStack(CallStack stack) {
        DocprocExecutor ex = ((getExecutor() == null) ? new DocprocExecutor(getName(), stack) : new DocprocExecutor(getExecutor(), stack));
        setExecutor(ex);
    }

    /**
     * Asynchronously process the given Processing using the processing
     * chain of this service, and call the specified ProcessingEndpoint when done.
     *
     * @throws RuntimeException caused by a QueueFullException if this DocprocService has a bounded input queue and the queue is full
     * @throws IllegalStateException if this DocprocService is not accepting new incoming processings
     */
    public void process(Processing processing, ProcessingEndpoint endp) {
        processing.setServiceName(getName());
        ((ProcessingAccess)processing).setCallStack(new CallStack(getCallStack()));
        ((ProcessingAccess)processing).setEndpoint(endp);
        addProcessing(processing);
    }

    /**
     * Asynchronously process the given Processing using the processing
     * chain of this service
     *
     * @throws RuntimeException caused by a QueueFullException if this DocprocService has a bounded input queue and the queue is full
     * @throws IllegalStateException if this DocprocService is not accepting new incoming processings
     */
    public void process(Processing processing) {
        process(processing, null);
    }

    /**
     * Asynchronously process the given document put or document update using the processing
     * chain of this service, and call the specified ProcessingEndpoint when done.
     *
     * @throws RuntimeException caused by a QueueFullException if this DocprocService has a bounded input queue and the queue is full
     * @throws IllegalStateException if this DocprocService is not accepting new incoming processings
     */
    public void process(DocumentOperation documentOperation, ProcessingEndpoint endp) {
        Processing processing = new Processing(getName(), documentOperation, new CallStack(getCallStack()));
        ((ProcessingAccess)processing).setEndpoint(endp);
        addProcessing(processing);
    }

    /**
     * Asynchronously process the given document operation using the processing
     * chain of this service.
     *
     * @throws RuntimeException caused by a QueueFullException if this DocprocService has a bounded input queue and the queue is full
     * @throws IllegalStateException if this DocprocService is not accepting new incoming processings
     */
    public void process(DocumentOperation documentOperation) {
        process(documentOperation, null);
    }

    /**
     * Asynchronously process the given document operations as one unit
     * using the processing chain of this service,
     * and call the specified ProcessingEndpoint when done.
     *
     * @throws RuntimeException caused by a QueueFullException if this DocprocService has a bounded input queue and the queue is full
     * @throws IllegalStateException if this DocprocService is not accepting new incoming processings
     */
    public void processDocumentOperations(List<DocumentOperation> documentOperations, ProcessingEndpoint endp) {
        Processing processing = Processing.createProcessingFromDocumentOperations(getName(), documentOperations, new CallStack(getCallStack()));
        ((ProcessingAccess)processing).setEndpoint(endp);
        addProcessing(processing);

    }

    /**
     * Asynchronously process the given document operations as one unit
     * using the processing chain of this service.
     *
     * @throws RuntimeException caused by a QueueFullException if this DocprocService has a bounded input queue and the queue is full
     * @throws IllegalStateException if this DocprocService is not accepting new incoming processings
     */
    public void processDocumentOperations(List<DocumentOperation> documentOperations) {
        processDocumentOperations(documentOperations, null);
    }

    private void addProcessing(Processing processing) {
        if ( ! isAcceptingNewProcessings())
            throw new IllegalStateException("Docproc service " + getName() + 
                                            " is not accepting new incoming processings. Cannot add " + processing + " ");

        if ( ! queue.offer(processing))
            throw new RejectedExecutionException("Docproc service " + getName() + " is busy, please try later");
    }

    /**
     * <p>Do some work in this service. This will perform some processing and return
     * in a "short" while, as long as individual processors also returns.</p>
     *
     * <p>This method is thread safe - multiple threads may call doWork at any time.
     * Note that processors
     * should be non-blocking, so multiple threads should be used primarily to
     * utilize multiple processors.</p>
     *
     * @return true if some work was performed, false if no work could be performed
     *         at this time, because there are no outstanding processings, or because
     *         this is out of service. Note that since processings may arrive or be put
     *         back by another thread at any time, this return value does not mean
     *         that no work will be available if doWork as called again immediately.
     */
    public boolean doWork() {
        try {
            return doWork(false);
        } catch (InterruptedException e) {
            //will never happen because we are not blocking
            throw new RuntimeException(e);
        }
    }

    private boolean doWork(boolean blocking) throws InterruptedException {
        Processing processing;
        if (blocking) {
            processing = queue.take();
        } else {
            processing = queue.poll();
        }

        if (processing == null) {
            //did no work, returning nothing to queue
            return false;
        }
        if (!isInService()) {
            //did no work, returning processing (because it's not empty)
            queue.add(processing);  //always successful, since queue is unbounded
            return false;
        }

        boolean remove = workOn(processing);  //NOTE: Exceptions are handled in here, but not Errors
        if (!remove) {
            queue.add(processing);  //always successful, since queue is unbounded
        }
        return true;
        //NOTE: We *could* have called returnProcessing() in a finally block here, but we don't
        //want that, since the only thing being thrown out here is Errors, and then the Processing
        //can just disappear instead
    }

    /**
     * <p>Do some work in this service. This will perform some processing and return
     * in a "short" while, as long as individual processors also returns.&nbsp;Note that
     * if the internal queue is empty when this method is called, it will block until
     * some work is submitted by a call to process() by another thread.</p>
     * 
     * <p>This method is thread safe - multiple threads may call doWorkBlocking at any time.
     * Note that processors
     * should be non-blocking, so multiple threads should be used primarily to
     * utilize multiple processors.</p>
     *
     * @return always true, since if the internal queue is empty when this method is
     *         called, it will block until some work is submitted by a call to
     *         process() by another thread.
     * @throws InterruptedException if a call to this method is interrupted while waiting for data to become available
     */
    public boolean doWorkBlocking() throws InterruptedException {
        return doWork(true);
    }

    /**
     * Do some work on this processing. Must only be called from the worker thread.
     *
     * @return true if this processing should be removed, false if there is more work to do on it later
     * @throws NoCallStackException if no CallStack has been set on this executor.
     */
    boolean workOn(Processing processing) {
        DocprocExecutor ex = getExecutor();
        if (ex == null) {
            throw new NoCallStackException();
        }

        DocumentProcessor.Progress progress;

        try {
            progress = ex.process(processing);
        } catch (Exception e) {
            processingFailed(processing, processing + " failed", e);
            return true;
        }

        if (DocumentProcessor.Progress.DONE.equals(progress)) {
            //notify endpoint
            ProcessingEndpoint recv = ((ProcessingAccess)processing).getEndpoint();
            if (recv != null) {
                recv.processingDone(processing);
            }
            return true;
        } else if (DocumentProcessor.Progress.FAILED.equals(progress)) {
            processingFailed(processing, processing + " failed at " + processing.callStack().getLastPopped(), null);
            return true;
        } else if (DocumentProcessor.Progress.PERMANENT_FAILURE.equals(progress)) {
            processingFailed(processing,
                    processing + " failed PERMANENTLY at " + processing.callStack().getLastPopped() + ", disabling processing service.", null);
            setInService(false);
            return true;
        } else {
            //LATER:
            return false;
        }
    }

    private void processingFailed(Processing processing, String errorMsg, Exception e) {
        if (e != null) {
            if (e instanceof HandledProcessingException) {
                errorMsg += ". Error message: " + e.getMessage();
                log.log(Level.WARNING, errorMsg);
                log.log(Level.FINE, "Chained exception:", e);
            } else {
                log.log(Level.WARNING, errorMsg, e);
            }
        } else {
            log.log(Level.WARNING, errorMsg);
        }

        //notify endpoint
        ProcessingEndpoint recv = ((ProcessingAccess)processing).getEndpoint();
        if (recv != null) {
            recv.processingFailed(processing, e);
        }
    }

    private class NoCallStackException extends RuntimeException {
    }

}
