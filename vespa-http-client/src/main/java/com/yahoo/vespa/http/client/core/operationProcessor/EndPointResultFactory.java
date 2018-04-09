// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.yahoo.vespa.http.client.Result;
import com.yahoo.vespa.http.client.config.Endpoint;
import com.yahoo.vespa.http.client.core.EndpointResult;
import com.yahoo.vespa.http.client.core.OperationStatus;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Einar M R Rosenvinge
 */
public final class EndPointResultFactory {

    private static Logger log = Logger.getLogger(EndPointResultFactory.class.getName());

    private static final String EMPTY_MESSAGE = "-";

    public static Collection<EndpointResult> createResult(
            Endpoint endpoint, InputStream inputStream) throws IOException {
        List<EndpointResult> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.US_ASCII))) {
            String line;
            while ((line = reader.readLine()) != null) {
                results.add(parseResult(line, endpoint));
            }
        }
        return results;
    }

    public static EndpointResult createError(
            Endpoint endpoint, String operationId, Exception exception) {
        return new EndpointResult(operationId, new Result.Detail(
                endpoint,  Result.ResultType.FATAL_ERROR, null, exception));
    }

    public static EndpointResult createTransientError(
            Endpoint endpoint, String operationId, Exception exception) {
        return new EndpointResult(operationId, new Result.Detail(
                endpoint, Result.ResultType.TRANSITIVE_ERROR, null, exception));
    }

    private static Result.ResultType replyToResultType(OperationStatus reply) {
        final Result.ResultType resultType;
        // The ordering below is important, e.g. if success, it is never a transient error even if isTransient is true.
        if (reply.errorCode.isSuccess()) {
            return Result.ResultType.OPERATION_EXECUTED;
        }
        if (reply.isConditionNotMet) {
            return Result.ResultType.CONDITION_NOT_MET;
        }
        if (reply.errorCode.isTransient()) {
            return Result.ResultType.TRANSITIVE_ERROR;
        }
        return Result.ResultType.FATAL_ERROR;
    }

    private static EndpointResult parseResult(String line, Endpoint endpoint) {
        try {
            OperationStatus reply = OperationStatus.parse(line);
            String message;
            if (EMPTY_MESSAGE.equals(reply.message)) {
                message = null;
            } else {
                message = reply.message;
            }
            Exception exception = null;
            if (!reply.errorCode.isSuccess() && message != null) {
                exception = new RuntimeException(message);
            }
            if (reply.traceMessage != null && !reply.traceMessage.isEmpty()) {
                log.fine("Got trace message: " + reply.traceMessage);
            }
            return new EndpointResult(
                    reply.operationId,
                    new Result.Detail(endpoint,
                            replyToResultType(reply),
                            reply.traceMessage,
                            exception));
        } catch (Throwable t) {
            throw new IllegalArgumentException("Bad result line from server: '" + line + "'", t);
        }
    }

}
