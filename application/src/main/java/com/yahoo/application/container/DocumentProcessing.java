// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.api.annotations.Beta;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.docproc.impl.DocprocExecutor;
import com.yahoo.docproc.impl.DocprocService;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.jdisc.DocumentProcessingHandler;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.annotation.AnnotationType;
import com.yahoo.processing.execution.chain.ChainRegistry;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * For doing document processing with {@link JDisc}.
 *
 * @author Einar M R Rosenvinge
 */
@Beta
public final class DocumentProcessing {

    private final DocumentProcessingHandler handler;
    private final Map<String, DocumentType> documentTypes;

    DocumentProcessing(DocumentProcessingHandler handler) {
        this.handler = handler;
        documentTypes = retrieveDocumentTypes(handler.getDocumentTypeManager());
    }

    private static Map<String, DocumentType> retrieveDocumentTypes(DocumentTypeManager documentTypeManager) {
        Map<String, DocumentType> documentTypes = new HashMap<>() ;
         for (Iterator<DocumentType> i = documentTypeManager.documentTypeIterator(); i.hasNext();) {
            DocumentType type = i.next();
            documentTypes.put(type.getName(), type);
        }
        return Collections.unmodifiableMap(documentTypes);
    }

    /**
     * Processes the given Processing through the specified chain. Note that if one
     * {@link com.yahoo.docproc.DocumentProcessor DocumentProcessor} in the
     * chain returns a {@link com.yahoo.docproc.DocumentProcessor.LaterProgress DocumentProcessor.LaterProgress},
     * the calling thread will sleep for the duration
     * specified in {@link com.yahoo.docproc.DocumentProcessor.LaterProgress#getDelay() DocumentProcessor.LaterProgress#getDelay()},
     * and then run again. This method will hence return when a document processor returns
     * {@link com.yahoo.docproc.DocumentProcessor.Progress#DONE DocumentProcessor.Progress#DONE} or
     * {@link com.yahoo.docproc.DocumentProcessor.Progress#FAILED DocumentProcessor.Progress#FAILED}, throws an exception,
     * or if the calling thread is interrupted. This method will never return a
     * {@link com.yahoo.docproc.DocumentProcessor.LaterProgress DocumentProcessor.LaterProgress}.
     *
     * @param chain the specification of the chain to execute
     * @param processing the Processing to process
     * @return Progress.DONE or Progress.FAILED
     * @throws RuntimeException if one of the document processors in the chain throws, or if the calling thread is interrupted
     */
    public DocumentProcessor.Progress process(ComponentSpecification chain, com.yahoo.docproc.Processing processing) {
        DocprocExecutor executor = getExecutor(chain);
        return executor.processUntilDone(processing);
    }

    /**
     * Processes the given Processing through the specified chain. Note that if one
     * {@link com.yahoo.docproc.DocumentProcessor DocumentProcessor} in the
     * chain returns a {@link com.yahoo.docproc.DocumentProcessor.LaterProgress DocumentProcessor.LaterProgress},
     * it will be returned by this method. This method will hence return whenever a document processor returns any
     * {@link com.yahoo.docproc.DocumentProcessor.Progress DocumentProcessor.Progress}, or
     * throws an exception.
     *
     * @param chain the specification of the chain to execute
     * @param processing the Processing to process
     * @return any Progress
     * @throws RuntimeException if one of the document processors in the chain throws
     */
    public DocumentProcessor.Progress processOnce(ComponentSpecification chain, com.yahoo.docproc.Processing processing) {
        DocprocExecutor executor = getExecutor(chain);
        return executor.process(processing);
    }

    private DocprocExecutor getExecutor(ComponentSpecification chain) {
        DocprocService service = handler.getDocprocServiceRegistry().getComponent(chain);
        if (service == null) {
            throw new IllegalArgumentException("No such chain: " + chain);
        }
        return service.getExecutor();
    }

    /**
     * Returns a registry of configured docproc chains.
     *
     * @return a registry of configured docproc chains
     */
    public ChainRegistry<DocumentProcessor> getChains() {
        return handler.getChains();
    }

    public Map<String, DocumentType> getDocumentTypes() {
        return documentTypes;
    }

    public Map<String, AnnotationType> getAnnotationTypes() {
        return handler.getDocumentTypeManager().getAnnotationTypeRegistry().getTypes();
    }

}
