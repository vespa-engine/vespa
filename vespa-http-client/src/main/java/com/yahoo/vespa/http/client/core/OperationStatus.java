// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import com.google.common.annotations.Beta;
import com.google.common.base.Splitter;

import java.util.Iterator;


/**
 * Wrapper to represent the result of a single operation fed to Vespa.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 * @since 5.1
 */
@Beta
public final class OperationStatus {
    public final String message;
    public final String operationId;
    public final ErrorCode errorCode;
    public final String traceMessage;

    private static final char EOL = '\n';
    private static final char SEPARATOR = ' ';
    private static final Splitter spaceSep = Splitter.on(SEPARATOR);

    public OperationStatus(String message, String operationId, ErrorCode errorCode, String traceMessage) {
        this.message = message;
        this.operationId = operationId;
        this.errorCode = errorCode;
        this.traceMessage = traceMessage;
    }

    /**
     * Parse a single rendered OperationStatus. White space may be padded after
     * and before the given status.
     *
     * @param singleLine
     *            a rendered OperationStatus
     * @return an OperationStatus instance reflecting the input
     * @throws IllegalArgumentException
     *             if there are illegal input data characters or the status
     *             element has no corresponding value in the ErrorCode
     *             enumeration
     */
    public static OperationStatus parse(String singleLine) {
        // Do note there is specifically left room for more arguments after
        // the first in the serialized form.
        Iterator<String> input = spaceSep.split(singleLine.trim()).iterator();
        String operationId;
        ErrorCode errorCode;
        String message;
        String traceMessage = "";

        operationId = Encoder.decode(input.next(), new StringBuilder())
                .toString();
        errorCode = ErrorCode.valueOf(Encoder.decode(input.next(),
                new StringBuilder()).toString());
        message = Encoder.decode(input.next(), new StringBuilder()).toString();
        // We are backwards compatible, meaning it is ok not to supply the last argument.
        if (input.hasNext()) {
            traceMessage = Encoder.decode(input.next(), new StringBuilder()).toString();
        }
        return new OperationStatus(message, operationId, errorCode, traceMessage);
    }

    public String render() {
        StringBuilder s = new StringBuilder();
        Encoder.encode(operationId, s).append(SEPARATOR);
        Encoder.encode(errorCode.toString(), s).append(SEPARATOR);
        Encoder.encode(message, s).append(SEPARATOR);
        Encoder.encode(traceMessage, s).append(EOL);
        return s.toString();
    }
}
