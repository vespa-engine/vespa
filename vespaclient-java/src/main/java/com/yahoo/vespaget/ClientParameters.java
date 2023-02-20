// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaget;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;

import java.util.Iterator;
/**
 * This class contains the program parameters.
 *
 * @author bjorncs
 */
public class ClientParameters {

    // Determines if the help page should be presented
    public final boolean help;
    // Contains the document ids. Is backed by either a list iterator if the ids were given as CLI arguments or Scanner(System.in) if ids are provided by standard input.
    public final Iterator<String> documentIds;
    // Print only the id for retrieved documents
    public final boolean printIdsOnly;
    // Determines which document fields to retrieve. Default is '[document]'.
    public final String fieldSet;
    // The Vespa route
    public final String route;
    // Alternative way to specify the route using cluster name.
    public final String cluster;
    // The configuration id for message bus. Default "client".
    public final String configId;
    // Determines if the serialized document size should be printed
    public final boolean showDocSize;
    // Document request timeout
    public final double timeout;
    // Determines whether or not the document request can be resent
    public final boolean noRetry;
    // Vespa trace level
    public final int traceLevel;
    // Document request priority
    public final DocumentProtocol.Priority priority;
    // If full documents are printed, they will be printed as JSON (instead of XML)
    public final boolean jsonOutput;
    // Output JSON tensors in short form
    public final boolean tensorShortForm;
    // Output JSON tensorvalues directly
    public final boolean tensorDirectValues;

    private ClientParameters(
            boolean help, Iterator<String> documentIds, boolean printIdsOnly,
            String fieldSet, String route, String cluster, String configId,
            boolean showDocSize, double timeout, boolean noRetry, int traceLevel,
            DocumentProtocol.Priority priority, boolean jsonOutput, boolean tensorShortForm,
            boolean tensorDirectValues) {

        this.help = help;
        this.documentIds = documentIds;
        this.printIdsOnly = printIdsOnly;
        this.fieldSet = fieldSet;
        this.route = route;
        this.cluster = cluster;
        this.configId = configId;
        this.showDocSize = showDocSize;
        this.timeout = timeout;
        this.noRetry = noRetry;
        this.traceLevel = traceLevel;
        this.priority = priority;
        this.jsonOutput = jsonOutput;
        this.tensorShortForm = tensorShortForm;
        this.tensorDirectValues = tensorDirectValues;
    }

    public static class Builder {
        private boolean help;
        private Iterator<String> documentIds;
        private boolean printIdsOnly;
        private String fieldSet;
        private String route;
        private String cluster;
        private String configId;
        private boolean showDocSize;
        private double timeout;
        private boolean noRetry;
        private int traceLevel;
        private DocumentProtocol.Priority priority;
        private boolean jsonOutput = true;
        private boolean tensorShortForm;
        private boolean tensorDirectValues;

        public Builder setHelp(boolean help) {
            this.help = help;
            return this;
        }

        public Builder setDocumentIds(Iterator<String> documentIds) {
            this.documentIds = documentIds;
            return this;
        }

        public Builder setPrintIdsOnly(boolean printIdsOnly) {
            this.printIdsOnly = printIdsOnly;
            return this;
        }

        public Builder setFieldSet(String fieldSet) {
            this.fieldSet = fieldSet;
            return this;
        }

        public Builder setRoute(String route) {
            this.route = route;
            return this;
        }

        public Builder setCluster(String cluster) {
            this.cluster = cluster;
            return this;
        }

        public Builder setConfigId(String configId) {
            this.configId = configId;
            return this;
        }

        public Builder setShowDocSize(boolean showDocSize) {
            this.showDocSize = showDocSize;
            return this;
        }

        public Builder setTimeout(double timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder setNoRetry(boolean noRetry) {
            this.noRetry = noRetry;
            return this;
        }

        public Builder setTraceLevel(int traceLevel) {
            this.traceLevel = traceLevel;
            return this;
        }

        public Builder setPriority(DocumentProtocol.Priority priority) {
            this.priority = priority;
            return this;
        }

        public Builder setJsonOutput(boolean jsonOutput) {
            this.jsonOutput = jsonOutput;
            return this;
        }

        public Builder setTensorShortForm(boolean tensorShortForm) {
            this.tensorShortForm = tensorShortForm;
            return this;
        }

        public Builder setTensorDirectValues(boolean tensorDirectValues) {
            this.tensorDirectValues = tensorDirectValues;
            return this;
        }

        public ClientParameters build() {
            return new ClientParameters(
                    help, documentIds, printIdsOnly, fieldSet, route, cluster, configId,
                    showDocSize, timeout, noRetry, traceLevel, priority, jsonOutput, tensorShortForm, tensorDirectValues);
        }
    }


}
