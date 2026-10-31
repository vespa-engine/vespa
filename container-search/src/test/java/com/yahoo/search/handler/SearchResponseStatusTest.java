// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.handler;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.ErrorMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the HTTP status of results without hits whose content nodes failed during the query.
 */
public class SearchResponseStatusTest {

    @Test
    void partialConnectionFailureWithoutHitsIsSuccess() {
        assertEquals(200, status(resultWithErrors(connectionFailure(true))));
    }

    @Test
    void connectionFailureWithoutHitsIsUnavailable() {
        assertEquals(503, status(resultWithErrors(connectionFailure(false))));
    }

    @Test
    void partialConnectionFailureDoesNotHideAnotherClusterFailing() {
        assertEquals(503, status(resultWithErrors(connectionFailure(true), connectionFailure(false))));
    }

    private static ErrorMessage connectionFailure(boolean partial) {
        ErrorMessage error = ErrorMessage.createBackendCommunicationError("Connection reset, partial: " + partial);
        error.setPartial(partial);
        return error;
    }

    private static Result resultWithErrors(ErrorMessage ... errors) {
        Result result = new Result(new Query());
        for (ErrorMessage error : errors)
            result.hits().addError(error);
        return result;
    }

    private static int status(Result result) {
        return SearchHandler.getHttpResponseStatus(HttpRequest.createTestRequest("http://localhost/search/", Method.GET), result);
    }

}
