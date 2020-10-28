// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.document.DocumentOperation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A document processing. This contains the document(s) or document update(s) to process,
 * a map of processing context data and the processing instance to
 * invoke the next time any work needs to be done on this processing.
 *
 * @author bratseth
 */
public class Processing {

    /** The name of the service which owns this processing. Null is the same as "default" */
    private String service = null;

    /** The processors to call the next work is done on this processing */
    private CallStack callStack = null;

    /** The collection of documents or document updates processed by this. This is never null */
    private final List<DocumentOperation> documentOperations;

    /**
     * Documents or document updates which should be added to <code>documents</code> before
     * the next access, or null if documents or document updates have never been added to
     * this processing
     */
    private List<DocumentOperation> documentsToAdd = null;

    /** The processing context variables */
    private Map<String, Object> context = null;

    /** The endpoint of this processing. */
    private ProcessingEndpoint endpoint = null;

    /** The registry of docproc services. */
    private ComponentRegistry<DocprocService> docprocServiceRegistry = null;

    private boolean operationsGotten = false;

    /**
     * Create a Processing with no documents. Useful with DocprocService.process(Processing).
     * Note that the callstack is initially empty when using this constructor (but it is
     * set by DocprocService.process(Processing).)
     */
    public Processing() {
        this.documentOperations = new ArrayList<>(1);
    }

    /**
     * Create a Processing from the given document operation
     */
    public static Processing of(DocumentOperation documentOperation) {
        return new Processing(documentOperation);
    }

    private Processing(DocumentOperation documentOperation) {
        this();
        addDocumentOperation(documentOperation);
    }

    /**
     * Create a processing with one document. The given document put or document update will be the single
     * element in <code>documentOperations</code>.
     *
     * @param service           the unique name of the service processing this
     * @param documentOperation document operation (DocumentPut or DocumentUpdate)
     * @param callStack         the document processors to call in this processing.
     * @param endp              the endpoint of this processing
     */
    Processing(String service, DocumentOperation documentOperation, CallStack callStack, ProcessingEndpoint endp) {
        this.service = service;
        this.documentOperations = new ArrayList<>(1);
        documentOperations.add(documentOperation);
        this.callStack = callStack;
        this.endpoint = endp;
    }

    /**
     * Create a processing with one document. The given document put or document update will be the single
     * element in <code>documentOperations</code>.
     *
     * @param service           the unique name of the service processing this
     * @param documentOperation document operation (DocumentPut or DocumentUpdate)
     * @param callStack         the document processors to call in this processing.
     *                          This <b>tranfers ownership</b> of this structure
     *                          to this class. The caller <i>must not</i> modify it
     */
    public Processing(String service, DocumentOperation documentOperation, CallStack callStack) {
        this(service, documentOperation, callStack, null);
    }

    @SuppressWarnings("unused")
    private Processing(String service, List<DocumentOperation> documentOpsAndUpdates, CallStack callStack, ProcessingEndpoint endp, boolean unused) {
        this.service = service;
        this.documentOperations = new ArrayList<>(documentOpsAndUpdates.size());
        documentOperations.addAll(documentOpsAndUpdates);
        this.callStack = callStack;
        this.endpoint = endp;
    }

    static Processing createProcessingFromDocumentOperations(String service, List<DocumentOperation> documentOpsAndUpdates, CallStack callStack, ProcessingEndpoint endp) {
        return new Processing(service, documentOpsAndUpdates, callStack, endp, false);
    }

    /**
     *
     * @param service               the unique name of the service processing this
     * @param documentsAndUpdates   the document operation list. This <b>transfers ownership</b> of this list
     *                              to this class. The caller <i>must not</i> modify it
     * @param callStack             the document processors to call in this processing.
     *                              This <b>transfers ownership</b> of this structure
     *                              to this class. The caller <i>must not</i> modify it
     */
    public static Processing createProcessingFromDocumentOperations(String service, List<DocumentOperation> documentsAndUpdates, CallStack callStack) {
        return new Processing(service, documentsAndUpdates, callStack, null, false);
    }

    public ComponentRegistry<DocprocService> getDocprocServiceRegistry() {
        return docprocServiceRegistry;
    }

    public void setDocprocServiceRegistry(ComponentRegistry<DocprocService> docprocServiceRegistry) {
        this.docprocServiceRegistry = docprocServiceRegistry;
    }

    /** Returns the name of the service processing this. This will never return null */
    public String getServiceName() {
        if (service == null) return "default";
        return service;
    }

    /** Sets the name of the service processing this. */
    public void setServiceName(String service) {
        this.service = service;
    }

    /**
     * Convenience method for looking up and returning the service processing this. This might return null
     * if #getServiceName returns a name that is not registered in {@link com.yahoo.docproc.DocprocService}.
     *
     * @return the service processing this, or null if unknown.
     */
    public DocprocService getService() {
        if (docprocServiceRegistry != null) {
            return docprocServiceRegistry.getComponent(getServiceName());
        }
        return null;
    }

    /** Returns a context variable, or null if it is not set */
    public Object getVariable(String name) {
        if (context == null) return null;
        return context.get(name);
    }

    /**
     * Returns an iterator of all context variables that are set
     *
     * @return an iterator over objects of type Map.Entry
     */
    public Iterator<Map.Entry<String, Object>> getVariableAndNameIterator() {
        if (context == null) context = new HashMap<>();
        return context.entrySet().iterator();
    }

    /** Clears all context variables that have been set */
    public void clearVariables() {
        if (context == null) return;
        context.clear();
    }

    /** Sets a context variable. */
    public void setVariable(String name, Object value) {
        if (context == null) context = new java.util.HashMap<>();
        context.put(name, value);
    }

    public Object removeVariable(String name) {
        if (context == null) return null;
        return context.remove(name);
    }

    /** Returns true if this variable is present, even if it is null */
    public boolean hasVariable(String name) {
        return context != null && context.containsKey(name);
    }

    /**
     * Returns the ProcessingEndpoint that is called when this Processing is complete, if any.
     *
     * @return the ProcessingEndpoint, or null
     */
    ProcessingEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Sets the ProcessingEndpoint to be called when this Processing is complete.
     *
     * @param endpoint the ProcessingEndpoint to use
     */
    void setEndpoint(ProcessingEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void addDocumentOperation(DocumentOperation documentOperation) {
        if (documentsToAdd == null) documentsToAdd = new ArrayList<>(1);
        documentsToAdd.add(documentOperation);
    }

    private void updateDocumentOperations() {
        if (documentsToAdd != null) {
            documentOperations.addAll(documentsToAdd);
            documentsToAdd.clear();
        }
    }

    public List<DocumentOperation> getDocumentOperations() {
        updateDocumentOperations();
        return documentOperations;
    }

    /** Returns the processors to call in this processing */
    public CallStack callStack() {
        return callStack;
    }

    /**
     * Package-private method to set the callstack of this processing. Only to be used
     * by DocprocService.process(Processing).
     *
     * @param callStack the callstack to set
     */
    void setCallStack(CallStack callStack) {
        this.callStack = callStack;
    }

    public String toString() {
        String previousCall = "";
        if (callStack != null) {
            Call call = callStack.getLastPopped();
            if (call != null) {
                previousCall = "Last call: " + call;
            }
        }
        if (documentOperations.size() == 1) {
            return "Processing of " + documentOperations.get(0) + ". " + previousCall;
        } else {
            String listString = documentOperations.toString();
            if (listString.length() > 100) {
                listString = listString.substring(0, 99);
                listString += "...]";
            }

            return "Processing of " + listString + ". " + previousCall;
        }
    }

    List<DocumentOperation> getOnceOperationsToBeProcessed() {
        if (operationsGotten)
            return Collections.emptyList();

        operationsGotten = true;
        return getDocumentOperations();
    }
}
