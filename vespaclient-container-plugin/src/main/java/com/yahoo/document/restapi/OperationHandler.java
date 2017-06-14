// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.vespaxmlparser.VespaXMLFeedReader;

import java.util.Optional;

/**
 * Abstract the backend stuff for the REST API, such as retrieving or updating documents.
 *
 * @author dybis
 */
public interface OperationHandler {

    class VisitResult{

        public final Optional<String> token;
        public final String documentsAsJsonList;

        public VisitResult(Optional<String> token, String documentsAsJsonList) {
            this.token = token;
            this.documentsAsJsonList = documentsAsJsonList;
        }
    }

    VisitResult visit(RestUri restUri, String documentSelection,  Optional<String> cluster, Optional<String> continuation) throws RestApiException;

    void put(RestUri restUri, VespaXMLFeedReader.Operation data, Optional<String> route) throws RestApiException;

    void update(RestUri restUri, VespaXMLFeedReader.Operation data, Optional<String> route) throws RestApiException;

    void delete(RestUri restUri, String condition, Optional<String> route) throws RestApiException;

    Optional<String> get(RestUri restUri) throws RestApiException;
    
    /** Called just before this is disposed of */
    default void shutdown() {}

}
