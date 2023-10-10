// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * <p>Information on the outcome of <i>initiating</i> a send or forward on a session.
 * The result will tell if the send/forward was accepted or not. If it was accepted,
 * an (asynchroneous) reply is guaranteed to be delivered at some later time.
 * If it was not accepted, a <i>transient error</i> has occured. In that case,
 * {@link #getError} can be used to access the exact error.</p>
 *
 * <p>This class is <b>immutable</b>.
 *
 * @author bratseth
 */
public class Result {

    public static final Result ACCEPTED = new Result();
    private final Error error;

    /**
     * The default constructor is private so that the only error-results can be new'ed by
     * the user. All accepted results should use the public "accepted" constant.
     */
    private Result() {
        error = null;
    }

    /**
     * This constructor assigns a given error to the member variable such that this result
     * becomes unaccepted with a descriptive error.
     *
     * @param error The error to assign to this result.
     */
    public Result(Error error) {
        this.error = error;
    }

    /**
     * This constructor is a convencience function to allow simpler instantiation of a result that contains an error.
     * It does nothing but proxy the {@link #Result(Error)} function with a new instance of {@link Error}.
     *
     * @param code The numerical code of the error.
     * @param message The description of the error.
     */
    public Result(int code, String message) {
        this(new Error(code, message));
    }

    /**
     * Returns whether this message was accepted.
     * If it was accepted, a Reply is guaranteed to be produced for this message
     * at some later time. If it was not accepted, getError can be called to
     * investigate why.
     *
     * @return true if this message was accepted, false otherwise
     */
    public boolean isAccepted() {
        return error == null;
    }

    /**
     * The error resulting from this send/forward if the message was not accepted.
     *
     * @return The error is not accepcted, null if accepted.
     */
    public Error getError() {
        return error;
    }
}
