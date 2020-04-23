// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.docproc.proxy.SchemaMap;
import com.yahoo.document.DocumentTypeManager;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
//TODO Vespa 8 This class and a lot of other in this package should not be part of PublicAPI
public class DocprocService extends AbstractComponent {

    private volatile DocprocExecutor executor;

    private final ThreadPoolExecutor threadPool;
    /** The current state of this service */
    private boolean inService = false;
    /** The current state of this service */
    public static SchemaMap schemaMap = new SchemaMap();
    private DocumentTypeManager documentTypeManager = null;

    DocprocService(ComponentId id, int numThreads) {
        super(id);
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

    @Deprecated
    public DocprocService(ComponentId id, CallStack stack, DocumentTypeManager mgr) {
        this(id, stack, mgr, Runtime.getRuntime().availableProcessors());
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

}
