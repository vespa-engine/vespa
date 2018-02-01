// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.vespaxmlparser.VespaXMLFeedReader;

import java.util.Optional;

/**
 * Abstract the backend stuff for the REST API, such as retrieving or updating documents.
 *
 * @author Haakon Dybdahl
 */
public interface OperationHandler {

    class VisitResult {

        public final Optional<String> token;
        public final String documentsAsJsonList;

        public VisitResult(Optional<String> token, String documentsAsJsonList) {
            this.token = token;
            this.documentsAsJsonList = documentsAsJsonList;
        }
    }

    class VisitOptions {
        public final Optional<String> cluster;
        public final Optional<String> continuation;
        public final Optional<Integer> wantedDocumentCount;
        public final Optional<String> fieldSet;
        public final Optional<Integer> concurrency;

        /** @deprecated Use a VisitOptions.Builder instead */
        @Deprecated
        public VisitOptions(Optional<String> cluster, Optional<String> continuation, Optional<Integer> wantedDocumentCount) {
            this.cluster = cluster;
            this.continuation = continuation;
            this.wantedDocumentCount = wantedDocumentCount;
            this.fieldSet = Optional.empty();
            this.concurrency = Optional.empty();
        }

        private VisitOptions(Builder builder) {
            this.cluster = Optional.ofNullable(builder.cluster);
            this.continuation = Optional.ofNullable(builder.continuation);
            this.wantedDocumentCount = Optional.ofNullable(builder.wantedDocumentCount);
            this.fieldSet = Optional.ofNullable(builder.fieldSet);
            this.concurrency = Optional.ofNullable(builder.concurrency);
        }

        public static class Builder {
            String cluster;
            String continuation;
            Integer wantedDocumentCount;
            String fieldSet;
            Integer concurrency;

            public Builder cluster(String cluster) {
                this.cluster = cluster;
                return this;
            }

            public Builder continuation(String continuation) {
                this.continuation = continuation;
                return this;
            }

            public Builder wantedDocumentCount(Integer count) {
                this.wantedDocumentCount = count;
                return this;
            }

            public Builder fieldSet(String fieldSet) {
                this.fieldSet = fieldSet;
                return this;
            }

            public Builder concurrency(Integer concurrency) {
                this.concurrency = concurrency;
                return this;
            }

            public VisitOptions build() {
                return new VisitOptions(this);
            }
        }

        public static Builder builder() {
            return new Builder();
        }
    }

    VisitResult visit(RestUri restUri, String documentSelection, VisitOptions options) throws RestApiException;

    void put(RestUri restUri, VespaXMLFeedReader.Operation data, Optional<String> route) throws RestApiException;

    void update(RestUri restUri, VespaXMLFeedReader.Operation data, Optional<String> route) throws RestApiException;

    void delete(RestUri restUri, String condition, Optional<String> route) throws RestApiException;

    Optional<String> get(RestUri restUri) throws RestApiException;
    
    /** Called just before this is disposed of */
    default void shutdown() {}

}
