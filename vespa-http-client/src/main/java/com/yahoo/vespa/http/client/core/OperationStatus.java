// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import com.google.common.annotations.Beta;
import com.google.common.base.Splitter;

import java.util.Iterator;


/**
 * Serialization/deserialization class for the result of a single document operation against Vespa.
 *
 * @author Steinar Knutsen
 */
@Beta
public final class OperationStatus {

    public static final String IS_CONDITION_NOT_MET = "IS-CONDITION-NOT-MET";
    public final String message;
    public final String operationId;
    public final ErrorCode errorCode;
    public final String traceMessage;
    public final boolean isConditionNotMet;

    private static final char EOL = '\n';
    private static final char SEPARATOR = ' ';
    private static final Splitter spaceSep = Splitter.on(SEPARATOR);

    /**
     * Constructor
     * @param message some human readable information what happened
     * @param operationId the doc ID for the operation
     * @param errorCode if it is success, transitive, or fatal
     * @param isConditionNotMet if error is due to condition not met
     * @param traceMessage any tracemessage
     */
    public OperationStatus(String message, String operationId, ErrorCode errorCode, boolean isConditionNotMet, String traceMessage) {
        this.isConditionNotMet = isConditionNotMet;
        this.message = message;
        this.operationId = operationId;
        this.errorCode = errorCode;
        this.traceMessage = traceMessage;
    }

    /**
     * Parse a single rendered OperationStatus string. White space may be padded after
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
        boolean isConditionNotMet = false;
        if (message.startsWith(IS_CONDITION_NOT_MET)) {
            message = message.replaceFirst(IS_CONDITION_NOT_MET, "");
            isConditionNotMet = true;
        }
        if (input.hasNext()) {
            traceMessage = Encoder.decode(input.next(), new StringBuilder()).toString();
        }
        return new OperationStatus(message, operationId, errorCode, isConditionNotMet, traceMessage);
    }

    /** Returns a string representing the status. */
    public String render() {
        StringBuilder s = new StringBuilder();
        Encoder.encode(operationId, s).append(SEPARATOR);
        Encoder.encode(errorCode.toString(), s).append(SEPARATOR);
        Encoder.encode(isConditionNotMet ? IS_CONDITION_NOT_MET + message : message, s).append(SEPARATOR);
        Encoder.encode(traceMessage, s).append(EOL);
        return s.toString();
    }

}
