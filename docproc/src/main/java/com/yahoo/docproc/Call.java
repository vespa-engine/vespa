// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import ai.vespa.metrics.ContainerMetrics;
import com.yahoo.component.ComponentId;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.docproc.impl.DocumentOperationWrapper;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.docproc.proxy.ProxyDocument;
import com.yahoo.docproc.proxy.ProxyDocumentUpdate;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.jdisc.Metric;

import java.util.List;
import java.util.Map;

/**
 * A document processor to call - an item on a {@link com.yahoo.docproc.CallStack}.
 *
 * @author bratseth
 * @author Einar M R Rosenvinge
 */
public class Call implements Cloneable {

    private final DocumentProcessor processor;
    private final String PROC_TIME_METRIC_NAME = ContainerMetrics.DOCPROC_PROC_TIME.baseName();
    private final String PROC_DOC_COUNT_METRIC_NAME = ContainerMetrics.DOCPROC_DOCUMENTS.baseName();
    private final Metric metric;
    private final Metric.Context metricContext;

    public Call(DocumentProcessor processor) {
        this(processor, new NullMetric());
    }

    /**
     * Creates a new call to a processor with no arguments.
     *
     * @param processor the document processor to call
     */
    public Call(DocumentProcessor processor, Metric metric) {
        this(processor, "", metric);
    }

    public Call(DocumentProcessor processor, String chainName, Metric metric) {
        this.processor = processor;
        if (chainName == null)
            chainName = "";
        this.metric = metric;
        this.metricContext = metric.createContext(Map.of(
                "chain", chainName,
                "docproc", getDocumentProcessorId().stringValue()
        ));
    }

    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException("Will not happen");
        }
    }

    /**
     * Returns the processor to call.
     *
     * @return a reference to the processor to call
     */
    public DocumentProcessor getDocumentProcessor() {
        return processor;
    }

    /**
     * Returns the ID of the processor to call.
     *
     * @return the ID of the processor to call
     */
    public ComponentId getDocumentProcessorId() {
        return processor.getId();
    }

    /**
     * The Document object the proc should work on. Normally the one in arguments, but could be a proxy object
     * if schema mapping or @Accesses is in effect.
     *
     * <p>
     * Public for testing
     */
    public DocumentPut configDoc(DocumentProcessor docProc, DocumentPut documentPut) {
        if ( ! docProc.getFieldMap().isEmpty() || docProc.hasAnnotations()) {
            Document document = documentPut.getDocument();
            document = new ProxyDocument(docProc, document, docProc.getDocMap(document.getDataType().getName()));

            DocumentPut newDocumentPut = new DocumentPut(document);
            newDocumentPut.setCondition(documentPut.getCondition());
            documentPut = newDocumentPut;
        }

        return documentPut;
    }

    /**
     * The DocumentUpdate object a processor should work on. The one in args, or schema mapped.
     *
     * @return a DocumentUpdate
     */
    private DocumentUpdate configDocUpd(DocumentProcessor proc, DocumentUpdate docU) {
        if (proc.getFieldMap().isEmpty()) return docU;
        return new ProxyDocumentUpdate(docU, proc.getDocMap(docU.getDocumentType().getName()));
    }

    private void schemaMapProcessing(Processing processing) {
        final List<DocumentOperation> documentOperations = processing.getDocumentOperations();
        for (int i = 0; i < documentOperations.size(); i++) {
            DocumentOperation op = documentOperations.get(i);
            if (op instanceof DocumentPut) {
                documentOperations.set(i, configDoc(processor, (DocumentPut) op));
            } else if (op instanceof DocumentUpdate) {
                documentOperations.set(i, configDocUpd(processor, (DocumentUpdate) op));
            }
        }
    }


    private void unwrapSchemaMapping(Processing processing) {
        final List<DocumentOperation> documentOperations = processing.getDocumentOperations();

        for (int i = 0; i < documentOperations.size(); i++) {
            DocumentOperation documentOperation = documentOperations.get(i);

            if (documentOperation instanceof DocumentPut) {
                DocumentPut putOperation = (DocumentPut) documentOperation;

                if (putOperation.getDocument() instanceof DocumentOperationWrapper) {
                    DocumentOperationWrapper proxy = (DocumentOperationWrapper) putOperation.getDocument();
                    documentOperations.set(i, new DocumentPut(putOperation, ((DocumentPut)proxy.getWrappedDocumentOperation()).getDocument()));
                }
            }
        }
    }

    /**
     * Call the DocumentProcessor of this call.
     *
     * @param processing the Processing object to use
     * @return the progress of the DocumentProcessor that was called
     */
    public DocumentProcessor.Progress call(Processing processing) {
        try {
            int numDocs = processing.getDocumentOperations().size();
            schemaMapProcessing(processing);
            long startTime = SystemTimer.INSTANCE.milliTime();
            DocumentProcessor.Progress retval = processor.process(processing);
            long procTime = SystemTimer.INSTANCE.milliTime() - startTime;
            updateMetrics(procTime, numDocs);
            return retval;
        } finally {
            unwrapSchemaMapping(processing);
        }
    }

    public String toString() {
        return "call to class " + processor.getClass().getName() + " (id: " + getDocumentProcessorId() + ")";
    }

    private void updateMetrics(long procTime, long documentCount) {
        metric.set(PROC_DOC_COUNT_METRIC_NAME, documentCount, metricContext);
        metric.set(PROC_TIME_METRIC_NAME, procTime, metricContext);
    }

}
