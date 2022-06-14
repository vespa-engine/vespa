// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.ErrorCode;

/**
 * The <i>synchronous</i> result of submitting an asynchronous operation.
 * A result is either a success or not. If it is not a success, it will contain an explanation of why.
 * Document repositories may return subclasses which contain more information.
 *
 * @author bratseth
 */
public class Result {

    /** Null if this is a success, set to the error occurring if this is a failure */
    private final Error error;

    /** The id of this operation */
    private final long requestId;

    private final ResultType type;

    /** Creates a successful result with requestId zero */
    public Result() {
        this(0);

    }
    /**
     * Creates a successful result
     *
     * @param requestId the ID of the request
     */
    public Result(long requestId) {
        this.error = null;
        this.requestId = requestId;
        type = ResultType.SUCCESS;
    }

    /**
     * Creates a unsuccessful result
     *
     * @param type  the type of failure
     * @param error the error to encapsulate in this Result
     * @see com.yahoo.documentapi.Result.ResultType
     */
    public Result(ResultType type, Error error) {
        this.type = type;
        this.error = error;
        this.requestId = 0;
    }

    /**
     * Returns whether this operation is a success.
     * If it is a success, the operation is accepted and one or more responses are guaranteed
     * to arrive within this sessions timeout limit.
     * If this is not a success, this operation has no further consequences.
     *
     * @return true if success
     */
    public boolean isSuccess() { return type == ResultType.SUCCESS; }

    public Error error() { return error; }

    /**
     * Returns the id of this operation. The asynchronous response to this operation
     * will contain the same id to allow clients who desire to, to match operations to responses.
     *
     * @return the id of this operation
     */
    public long getRequestId() { return requestId; }

    /**
     * Returns the type of result.
     *
     * @return the type of result, typically if this is an error or a success, and what kind of error.
     * @see com.yahoo.documentapi.Result.ResultType
     */
    public ResultType type() { return type;}

    /** The types that a Result can have. */
    public enum ResultType {
        /** The request was successful, no error information is attached. */
        SUCCESS,
        /** The request failed, but may be successful if retried at a later time. */
        TRANSIENT_ERROR,
        /** The request failed, and retrying is pointless. */
        FATAL_ERROR
    }

    public static Error toError(ResultType result) {
        switch (result) {
            case TRANSIENT_ERROR:
                return new Error(ErrorCode.TRANSIENT_ERROR, ResultType.TRANSIENT_ERROR.name());
            case FATAL_ERROR:
                return new Error(ErrorCode.FATAL_ERROR, ResultType.FATAL_ERROR.name());
        }
        return new Error(ErrorCode.NONE, "SUCCESS");
    }

}
