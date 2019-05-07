// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.vespaxmlparser.FeedOperation;

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
        public final Optional<String> bucketSpace;

        private VisitOptions(Builder builder) {
            this.cluster = Optional.ofNullable(builder.cluster);
            this.continuation = Optional.ofNullable(builder.continuation);
            this.wantedDocumentCount = Optional.ofNullable(builder.wantedDocumentCount);
            this.fieldSet = Optional.ofNullable(builder.fieldSet);
            this.concurrency = Optional.ofNullable(builder.concurrency);
            this.bucketSpace = Optional.ofNullable(builder.bucketSpace);
        }

        public static class Builder {
            String cluster;
            String continuation;
            Integer wantedDocumentCount;
            String fieldSet;
            Integer concurrency;
            String bucketSpace;

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

            public Builder bucketSpace(String bucketSpace) {
                this.bucketSpace = bucketSpace;
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

    void put(RestUri restUri, FeedOperation data, Optional<String> route) throws RestApiException;

    void update(RestUri restUri, FeedOperation data, Optional<String> route) throws RestApiException;

    void delete(RestUri restUri, String condition, Optional<String> route) throws RestApiException;

    Optional<String> get(RestUri restUri) throws RestApiException;

    default Optional<String> get(RestUri restUri, Optional<String> fieldSet) throws RestApiException {
        return get(restUri);
    }
    
    /** Called just before this is disposed of */
    default void shutdown() {}

}
