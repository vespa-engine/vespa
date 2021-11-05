// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.docproc.proxy.SchemaMap;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.jdisc.Metric;

/**
 * Class to hold parameters given to DocumentProcessingHandler, typically used by unit tests.
 *
 * @author Einar M R Rosenvinge
 * @see com.yahoo.docproc.jdisc.DocumentProcessingHandler
 */
public class DocumentProcessingHandlerParameters {

    private int maxNumThreads = 0;
    private DocumentTypeManager documentTypeManager = null;
    private ChainsModel chainsModel = null;
    private SchemaMap schemaMap = null;
    private Metric metric = new NullMetric();
    private ContainerDocumentConfig containerDocConfig;



    public Metric getMetric() {
        return metric;
    }

    public DocumentProcessingHandlerParameters setMetric(Metric metric) {
        this.metric = metric;
        return this;
    }

    /**
     * Returns the maximum number of thread that the thread pool will ever attempt to run simultaneously.
     *
     * @return the maximum number of thread that the thread pool will ever attempt to run simultaneously.
     */
    public int getMaxNumThreads() {
        return maxNumThreads;
    }

    public DocumentProcessingHandlerParameters setMaxNumThreads(int maxNumThreads) {
        this.maxNumThreads = maxNumThreads;
        return this;
    }

    public DocumentTypeManager getDocumentTypeManager() {
        return documentTypeManager;
    }

    public DocumentProcessingHandlerParameters setDocumentTypeManager(DocumentTypeManager documentTypeManager) {
        this.documentTypeManager = documentTypeManager;
        return this;
    }

    /**
     * Returns the chains model, used to build call stacks.
     * @return the chains model, used to build call stacks.
     */
    public ChainsModel getChainsModel() {
        return chainsModel;
    }

    public DocumentProcessingHandlerParameters setChainsModel(ChainsModel chainsModel) {
        this.chainsModel = chainsModel;
        return this;
    }

    /**
     * Returns the schema map to be used by the docproc handler.
     *
     * @return the schema map to be used by the docproc handler.
     */
    public SchemaMap getSchemaMap() {
        return schemaMap;
    }

    public DocumentProcessingHandlerParameters setSchemaMap(SchemaMap schemaMap) {
        this.schemaMap = schemaMap;
        return this;
    }

    public DocumentProcessingHandlerParameters setContainerDocumentConfig(ContainerDocumentConfig containerDocConfig) {
        this.containerDocConfig = containerDocConfig;
        return this;
    }

    public ContainerDocumentConfig getContainerDocConfig() {
        return containerDocConfig;
    }

}
