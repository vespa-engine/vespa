// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result;

import com.yahoo.container.protect.Error;

import static com.yahoo.container.protect.Error.*;


/**
 * An error message with a code. Use create methods to create messages.
 * The identity of an error message is determined by its values.
 *
 * @author bratseth
 */
public class ErrorMessage extends com.yahoo.processing.request.ErrorMessage {

    public static final int NULL_QUERY = Error.NULL_QUERY.code;

    /** The source producing this error, not always set */
    private String source = null;

    public ErrorMessage(int code, String message) {
        super(code, message);
    }

    /**
     * Creates an application specific error message with an application specific code.
     * If the error results from an exception a message which includes information from all nested (cause) exceptions
     * can be generated using com.yahoo.protect.Exceptions.toMessageString(exception).
     */
    public ErrorMessage(int code, String message, String detailedMessage) {
        super(code, message, detailedMessage);
    }

    /**
     * Creates an application specific error message with an application specific code and a stack trace.
     * This should only be used when there is useful information in the cause, i.e when the exception
     * is not expected. Applications rarely need to handle unexpected exceptions as this is done by the framework.
     */
    public ErrorMessage(int code, String message, String detailedMessage, Throwable cause) {
        super(code, message, detailedMessage, cause);
    }

    /** Creates an error message indicating that some backend service is unreachable */
    public static ErrorMessage createNoBackendsInService(String detailedMessage) {
        return new ErrorMessage(NO_BACKENDS_IN_SERVICE.code, "No backends in service. Try later", detailedMessage);
    }

    /** Creates an error message indicating that a null query was attempted evaluated */
    public static ErrorMessage createNullQuery(String detailedMessage) {
        return new ErrorMessage(NULL_QUERY, "Null query", detailedMessage);
    }

    /** Creates an error message indicating that the request is too large */
    public static ErrorMessage createRequestTooLarge(String detailedMessage) {
        return new ErrorMessage(REQUEST_TOO_LARGE.code, "Request too large", detailedMessage);
    }

    /** Creates an error message indicating that an illegal query was attempted evaluated. */
    public static ErrorMessage createIllegalQuery(String detailedMessage) {
        return new ErrorMessage(ILLEGAL_QUERY.code, "Illegal query", detailedMessage);
    }

    /** Creates an error message indicating that an invalid request parameter was received. */
    public static ErrorMessage createInvalidQueryParameter(String detailedMessage) {
        return new ErrorMessage(INVALID_QUERY_PARAMETER.code, "Invalid query parameter", detailedMessage);
    }

    /** Creates an error message indicating that an invalid request parameter was received. */
    public static ErrorMessage createInvalidQueryParameter(String detailedMessage, Throwable cause) {
        return new ErrorMessage(INVALID_QUERY_PARAMETER.code, "Invalid query parameter", detailedMessage, cause);
    }

    /** Creates a generic message used when there is no information available on the category of the error. */
    public static ErrorMessage createUnspecifiedError(String detailedMessage) {
        return new ErrorMessage(UNSPECIFIED.code, "Unspecified error", detailedMessage);
    }

    /** Creates a generic message used when there is no information available on the category of the error. */
    public static ErrorMessage createUnspecifiedError(String detailedMessage, Throwable cause) {
        return new ErrorMessage(UNSPECIFIED.code, "Unspecified error", detailedMessage, cause);
    }

    /** Creates a general error from an application components. */
    public static ErrorMessage createErrorInPluginSearcher(String detailedMessage) {
        return new ErrorMessage(ERROR_IN_PLUGIN.code, "Error in plugin Searcher", detailedMessage);
    }

    /** Creates a general error from an application component. */
    public static ErrorMessage createErrorInPluginSearcher(String detailedMessage, Throwable cause) {
        return new ErrorMessage(ERROR_IN_PLUGIN.code, "Error in plugin Searcher", detailedMessage, cause);
    }

    /** Creates an error indicating that an invalid query transformation was attempted. */
    public static ErrorMessage createInvalidQueryTransformation(String detailedMessage) {
        return new ErrorMessage(INVALID_QUERY_TRANSFORMATION.code, "Invalid query transformation",detailedMessage);
    }

    /** Creates an error indicating that the server is misconfigured */
    public static ErrorMessage createServerIsMisconfigured(String detailedMessage) {
        return new ErrorMessage(SERVER_IS_MISCONFIGURED.code, "Service is misconfigured", detailedMessage);
    }

    /** Creates an error indicating that there was a general error communicating with a backend service. */
    public static ErrorMessage createBackendCommunicationError(String detailedMessage) {
        return new ErrorMessage(BACKEND_COMMUNICATION_ERROR.code, "Backend communication error", detailedMessage);
    }

    /** Creates an error indicating that a node could not be pinged. */
    public static ErrorMessage createNoAnswerWhenPingingNode(String detailedMessage) {
        return new ErrorMessage(NO_ANSWER_WHEN_PINGING_NODE.code, "No answer when pinging node", detailedMessage);
    }

    public static final int timeoutCode = Error.TIMEOUT.code;
    /** Creates an error indicating that a request to a backend timed out. */
    public static ErrorMessage createTimeout(String detailedMessage) {
        return new ErrorMessage(timeoutCode, "Timed out", detailedMessage);
    }

    public static final int emptyDocsumsCode = Error.EMPTY_DOCUMENTS.code;
    /** Creates an error indicating that a request to a backend returned empty document content data. */
    public static ErrorMessage createEmptyDocsums(String detailedMessage) {
        return new ErrorMessage(emptyDocsumsCode, "Empty document summaries",detailedMessage);
    }

    /**
     * Creates an error indicating that the requestor is not authorized to perform the requested operation.
     * If this error is present, a HTTP layer will return 401.
     */
    public static ErrorMessage createUnauthorized(String detailedMessage) {
        return new ErrorMessage(UNAUTHORIZED.code, "Client not authenticated.", detailedMessage);
    }

    /**
     * Creates an error indicating that a forbidden operation was requested.
     * If this error is present, a HTTP layer will return 403.
     */
    public static ErrorMessage createForbidden(String detailedMessage) {
        return new ErrorMessage(FORBIDDEN.code, "Forbidden.", detailedMessage);
    }

    /**
     * Creates an error indicating that the requested resource was not found.
     * If this error is present, a HTTP layer will return 404.
     */
    public static ErrorMessage createNotFound(String detailedMessage) {
        return new ErrorMessage(NOT_FOUND.code, "Resource not found.", detailedMessage);
    }

    /**
     * Creates an error analog to HTTP bad request. If this error is present, a
     * HTTP layer will return 400.
     */
    public static ErrorMessage createBadRequest(String detailedMessage) {
        return new ErrorMessage(BAD_REQUEST.code, "Bad request.", detailedMessage);
    }

    /**
     * Creates an error analog to HTTP internal server error. If this error is present, a
     * HTTP layer will return 500.
     */
    public static ErrorMessage createInternalServerError(String detailedMessage) {
        return new ErrorMessage(INTERNAL_SERVER_ERROR.code, "Internal server error.", detailedMessage);
    }

    /** Wraps an error message received in a SearchReply packet */
    public static ErrorMessage createSearchReplyError(String detailedMessage) {
        return new ErrorMessage(RESULT_HAS_ERRORS.code, "Error in search reply.", detailedMessage);
    }

    /** Wraps an error message received in a DocsumReply packet */
    public static ErrorMessage createDocsumReplyError(String detailedMessage) {
        return new ErrorMessage(RESULT_HAS_ERRORS.code, "Error in fill reply.", detailedMessage);
    }

    /** Sets the source producing this error */
    public void setSource(String source) { this.source = source; }

    /** Returns the source producing this error, or null if no source is specified */
    public String getSource() { return source; }

    @Override
    public int hashCode() {
        return super.hashCode() + (source == null ? 0 : 31 * source.hashCode());
    }

    @Override
    public boolean equals(Object o) {
        if (!super.equals(o)) return false;

        ErrorMessage other = (ErrorMessage) o;
        if (this.source != null) {
            if (!this.source.equals(other.source)) return false;
        } else {
            if (other.source != null) return false;
        }

        return true;
    }

    @Override
    public String toString() {
        return (source==null ? "" : "Source '" + source + "': ") + super.toString();
    }

    @Override
    public ErrorMessage clone() {
        return (ErrorMessage)super.clone();
    }

    /**
     * Returns the given error message as this type. If it already is, this is a cast of the given instance.
     * Otherwise this creates a new instance having the same payload as the given instance.
     */
    public static ErrorMessage from(com.yahoo.processing.request.ErrorMessage error) {
        if (error instanceof ErrorMessage) return (ErrorMessage)error;
        return new ErrorMessage(error.getCode(),error.getMessage(),error.getDetailedMessage(),error.getCause());
    }

}
