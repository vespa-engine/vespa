// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.document.restapi.OperationHandler;
import com.yahoo.document.restapi.Response;
import com.yahoo.document.restapi.RestApiException;
import com.yahoo.document.restapi.RestUri;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;

import java.util.Optional;

/**
 * Mock that collects info about operation and returns them on second delete.
 */
public class MockedOperationHandler implements OperationHandler {

    StringBuilder log = new StringBuilder();
    int deleteCount = 0;

    @Override
    public VisitResult visit(RestUri restUri, String documentSelection, Optional<String> cluster, Optional<String> continuation) throws RestApiException {
        return new VisitResult(Optional.of("token"), "List of json docs, cont token " + continuation.map(a->a).orElse("not set") + ", doc selection: '"
                + documentSelection + "'");
    }

    @Override
    public void put(RestUri restUri, VespaXMLFeedReader.Operation data, Optional<String> route) throws RestApiException {
        log.append("PUT: " + data.getDocument().getId());
        log.append(data.getDocument().getBody().toString());
    }

    @Override
    public void update(RestUri restUri, VespaXMLFeedReader.Operation data, Optional<String> route) throws RestApiException {
        log.append("UPDATE: " + data.getDocumentUpdate().getId());
        log.append(data.getDocumentUpdate().getFieldUpdates().toString());
        if (data.getDocumentUpdate().getCreateIfNonExistent()) {
            log.append("[CREATE IF NON EXISTENT IS TRUE]");
        }
    }

    @Override
    public void delete(RestUri restUri, String condition, Optional<String> route) throws RestApiException {
        deleteCount++;
        if (deleteCount == 2) {
            String theLog = log.toString();
            log = new StringBuilder();
            deleteCount = 0;
            throw new RestApiException(Response.createErrorResponse(666, theLog));
        }
        log.append("DELETE: " + restUri.generateFullId());
    }

    @Override
    public Optional<String> get(RestUri restUri) throws RestApiException {
        log.append("GET: " + restUri.generateFullId());
        return Optional.empty();
    }

}
