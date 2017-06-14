// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc.jdisc;

import com.yahoo.component.chain.model.ChainsModel;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.docproc.proxy.SchemaMap;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.jdisc.Metric;
import com.yahoo.statistics.Statistics;

/**
 * Class to hold parameters given to DocumentProcessingHandler, typically used by unit tests.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @see com.yahoo.docproc.jdisc.DocumentProcessingHandler
 */
public class DocumentProcessingHandlerParameters {
    private int maxNumThreads = 0;
    private double maxConcurrentFactor = 0.2;
    private double documentExpansionFactor = 20.0;
    private int containerCoreMemoryMb = 50;
    private long maxQueueTimeMs = 0;
    private DocumentTypeManager documentTypeManager = null;
    private ChainsModel chainsModel = null;
    private SchemaMap schemaMap = null;
    private Statistics statisticsManager = Statistics.nullImplementation;
    private Metric metric = new NullMetric();
    private ContainerDocumentConfig containerDocConfig;

    public DocumentProcessingHandlerParameters() {
    }

    /**
     * Returns the number of megabytes of memory reserved for container core classes and data.
     *
     * @return the number of megabytes of memory reserved for container core classes and data.
     */
    public int getContainerCoreMemoryMb() {
        return containerCoreMemoryMb;
    }

    public DocumentProcessingHandlerParameters setContainerCoreMemoryMb(int containerCoreMemoryMb) {
        this.containerCoreMemoryMb = containerCoreMemoryMb;
        return this;
    }

    public Metric getMetric() {
        return metric;
    }

    public DocumentProcessingHandlerParameters setMetric(Metric metric) {
        this.metric = metric;
        return this;
    }

    /**
     * Returns the document expansion factor, i.e.&nbsp;by what factor a serialized and possibly compressed
     * input document is expected to expand during deserialization, including any temporary memory needed
     * when processing it.
     *
     * @return the document expansion factor.
     */
    public double getDocumentExpansionFactor() {
        return documentExpansionFactor;
    }

    public DocumentProcessingHandlerParameters setDocumentExpansionFactor(double documentExpansionFactor) {
        this.documentExpansionFactor = documentExpansionFactor;
        return this;
    }

    /**
     * Returns the max concurrent factor.
     *
     * @return the max concurrent factor.
     */
    public double getMaxConcurrentFactor() {
        return maxConcurrentFactor;
    }

    public DocumentProcessingHandlerParameters setMaxConcurrentFactor(double maxConcurrentFactor) {
        this.maxConcurrentFactor = maxConcurrentFactor;
        return this;
    }

    /**
     * Returns the maximum time (in milliseconds) that a document may stay in the input queue.&nbsp;The default value
     * of 0 disables this functionality.
     *
     * @return the maximum time (in milliseconds) that a document may stay in the input queue.
     */
    public long getMaxQueueTimeMs() {
        return maxQueueTimeMs;
    }

    public DocumentProcessingHandlerParameters setMaxQueueTimeMs(long maxQueueTimeMs) {
        this.maxQueueTimeMs = maxQueueTimeMs;
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

    public Statistics getStatisticsManager() {
        return statisticsManager;
    }

    public DocumentProcessingHandlerParameters setStatisticsManager(Statistics statisticsManager) {
        this.statisticsManager = statisticsManager;
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
