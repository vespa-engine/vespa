// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc;

import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>This class represents the single response (which may have any content model that a {@link RequestHandler} chooses
 * to implement) of some single request. Contrary to the {@link Request} class, this has no active reference to the
 * parent {@link Container} (this is tracked internally by counting the number of requests vs the number of responses
 * seen). The {@link ResponseHandler} of a Response is implicit in the invocation of {@link
 * RequestHandler#handleRequest(Request, ResponseHandler)}.</p>
 *
 * <p>The usage pattern of the Response is similar to that of the Request in that the {@link ResponseHandler} returns a
 * {@link ContentChannel} into which to write the Response content.</p>
 *
 * @author Simon Thoresen
 * @see Request
 * @see ResponseHandler
 */
public class Response {

    /**
     * <p>This interface acts as a namespace for the built-in status codes of the jDISC core. These are identical to the
     * common HTTP status codes (see <a href="http://www.rfc-editor.org/rfc/rfc2616.txt">RFC2616</a>).</p>
     */
    public interface Status {

        /**
         * <p>1xx: Informational - Request received, continuing process.</p>
         */
        int CONTINUE = 100;
        int SWITCHING_PROTOCOLS = 101;
        int PROCESSING = 102;

        /**
         * <p>2xx: Success - The action was successfully received, understood, and accepted.</p>
         */
        int OK = 200;
        int CREATED = 201;
        int ACCEPTED = 202;
        int NON_AUTHORITATIVE_INFORMATION = 203;
        int NO_CONTENT = 204;
        int RESET_CONTENT = 205;
        int PARTIAL_CONTENT = 206;
        int MULTI_STATUS = 207;

        /**
         * <p>3xx: Redirection - Further action must be taken in order to complete the request.</p>
         */
        int MULTIPLE_CHOICES = 300;
        int MOVED_PERMANENTLY = 301;
        int FOUND = 302;
        int SEE_OTHER = 303;
        int NOT_MODIFIED = 304;
        int USE_PROXY = 305;
        int TEMPORARY_REDIRECT = 307;

        /**
         * <p>4xx: Client Error - The request contains bad syntax or cannot be fulfilled.</p>
         */
        int BAD_REQUEST = 400;
        int UNAUTHORIZED = 401;
        int PAYMENT_REQUIRED = 402;
        int FORBIDDEN = 403;
        int NOT_FOUND = 404;
        int METHOD_NOT_ALLOWED = 405;
        int NOT_ACCEPTABLE = 406;
        int PROXY_AUTHENTICATION_REQUIRED = 407;
        int REQUEST_TIMEOUT = 408;
        int CONFLICT = 409;
        int GONE = 410;
        int LENGTH_REQUIRED = 411;
        int PRECONDITION_FAILED = 412;
        int REQUEST_TOO_LONG = 413;
        int REQUEST_URI_TOO_LONG = 414;
        int UNSUPPORTED_MEDIA_TYPE = 415;
        int REQUESTED_RANGE_NOT_SATISFIABLE = 416;
        int EXPECTATION_FAILED = 417;
        int INSUFFICIENT_SPACE_ON_RESOURCE = 419;
        int METHOD_FAILURE = 420;
        int UNPROCESSABLE_ENTITY = 422;
        int LOCKED = 423;
        int FAILED_DEPENDENCY = 424;

        /**
         * <p>5xx: Server Error - The server failed to fulfill an apparently valid request.</p>
         */
        int INTERNAL_SERVER_ERROR = 500;
        int NOT_IMPLEMENTED = 501;
        int BAD_GATEWAY = 502;
        int SERVICE_UNAVAILABLE = 503;
        int GATEWAY_TIMEOUT = 504;
        int VERSION_NOT_SUPPORTED = 505;
        int INSUFFICIENT_STORAGE = 507;
    }

    private final Map<String, Object> context = new HashMap<>();
    private final HeaderFields headers = new HeaderFields();
    private Throwable error;
    private int status;

    /**
     * <p>Creates a new instance of this class.</p>
     *
     * @param status The status code to assign to this.
     */
    public Response(int status) {
        this(status, null);
    }

    /**
     * <p>Creates a new instance of this class.</p>
     *
     * @param status The status code to assign to this.
     * @param error  The error to assign to this.
     */
    public Response(int status, Throwable error) {
        this.status = status;
        this.error = error;
    }

    /**
     * <p>Returns the named application context objects. This data is not intended for network transport, rather they
     * are intended for passing shared data between components of an Application.</p>
     *
     * <p>Modifying the context map is a thread-unsafe operation -- any changes made after calling {@link
     * ResponseHandler#handleResponse(Response)} might never become visible to other threads, and might throw
     * ConcurrentModificationExceptions in other threads.</p>
     *
     * @return The context map.
     */
    public Map<String, Object> context() {
        return context;
    }

    /**
     * <p>Returns the set of header fields of this Request. These are the meta-data of the Request, and are not applied
     * to any internal {@link Container} logic. Modifying headers is a thread-unsafe operation -- any changes made after
     * calling {@link ResponseHandler#handleResponse(Response)} might never become visible to other threads, and might
     * throw ConcurrentModificationExceptions in other threads.</p>
     *
     * @return The header fields.
     */
    public HeaderFields headers() {
        return headers;
    }

    /**
     * <p>Returns the status code of this response. This is an integer result code of the attempt to understand and
     * satisfy the corresponding {@link Request}. It is encouraged, although not enforced, to use the built-in {@link
     * Status} codes whenever possible.</p>
     *
     * @return The status code.
     * @see #setStatus(int)
     */
    public int getStatus() {
        return status;
    }

    /**
     * <p>Sets the status code of this response. This is an integer result code of the attempt to understand and
     * satisfy the corresponding {@link Request}. It is encouraged, although not enforced, to use the built-in {@link
     * Status} codes whenever possible. </p>
     *
     * <p>Because access to this field is not guarded by any lock, any changes made after calling {@link
     * ResponseHandler#handleResponse(Response)} might never become visible to other threads.</p>
     *
     * @param status The status code to set.
     * @return This, to allow chaining.
     * @see #getStatus()
     */
    public Response setStatus(int status) {
        this.status = status;
        return this;
    }

    /**
     * <p>Returns the error of this response, or null if none has been set. This is typically non-null if the status
     * indicates an unsuccessful response.</p>
     *
     * @return The error.
     * @see #getError()
     */
    public Throwable getError() {
        return error;
    }

    /**
     * <p>Sets the error of this response. It is encouraged, although not enforced, to use this field to attach
     * additional information to an unsuccessful response.</p>
     *
     * <p>Because access to this field is not guarded by any lock, any changes made after calling {@link
     * ResponseHandler#handleResponse(Response)} might never become visible to other threads.</p>
     *
     * @param error The error to set.
     * @return This, to allow chaining.
     * @see #getError()
     */
    public Response setError(Throwable error) {
        this.error = error;
        return this;
    }

    /**
     * This is a convenience method for creating a Response with status {@link Status#GATEWAY_TIMEOUT} and passing
     * that to the given {@link ResponseHandler#handleResponse(Response)}. For trivial implementations of {@link
     * RequestHandler#handleTimeout(Request, ResponseHandler)}, simply call this method.
     *
     * @param handler The handler to pass the timeout {@link Response} to.
     */
    public static void dispatchTimeout(ResponseHandler handler) {
        ResponseDispatch.newInstance(Status.GATEWAY_TIMEOUT).dispatch(handler);
    }
}
