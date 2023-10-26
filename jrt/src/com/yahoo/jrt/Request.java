// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * A Request bundles information about a single RPC invocation. A
 * Request contains the name of the method, the method parameters, the
 * method return values and also error information if something went
 * wrong. Request objects are used by both RPC clients and RPC
 * servers. An RPC client is the one requesting the invocation. An RPC
 * server is the one performing the invocation. The RPC client uses a
 * {@link Target} to invoke the request. The RPC server registers a
 * {@link Method} with the {@link Supervisor}. Note that RPC
 * client/server roles are independent of connection client/server
 * roles, since invocations can be performed both ways across a {@link
 * Target}.
 */
public class Request
{
    private String  methodName;
    private Values  parameters;
    private Values  returnValues = new Values();
    private int     errorCode    = 0;
    private String  errorMessage = null;
    private boolean detached     = false;
    private Object  context      = null;

    private InvocationServer serverHandler;
    private InvocationClient clientHandler;

    /**
     * Create a Request from a method name and a set of
     * parameters. This method is used internally on the server side
     * to create Requests for incoming invocations.
     *
     * @param methodName method name
     * @param parameters method parameters
     **/
    Request(String methodName, Values parameters) {
        this.methodName = methodName;
        this.parameters = parameters;
    }

    /**
     * Set the client invocation helper object for this request
     *
     * @param handler helper object
     **/
    void clientHandler(InvocationClient handler) {
        clientHandler = handler;
    }

    /**
     * Set the server invocation helper object for this request
     *
     * @param handler helper object
     **/
    void serverHandler(InvocationServer handler) {
        serverHandler = handler;
    }

    /**
     * Create a new Request with the given method name.
     *
     * @param methodName name of the method you want to invoke
     **/
    public Request(String methodName) {
        this(methodName, new Values());
    }

    /**
     * Set the application context associated with this request.
     *
     * @param context application context
     **/
    public void setContext(Object context) {
        this.context = context;
    }

    /**
     * Obtain the application context associated with this request.
     *
     * @return application context
     **/
    public Object getContext() {
        return context;
    }

    /**
     * Obtain the method name
     *
     * @return method name
     **/
    public String methodName() {
        return methodName;
    }

    /**
     * Obtain the parameters
     *
     * @return request parameters
     **/
    public Values parameters() {
        return parameters;
    }

    /**
     * Set the return values for this request. Used internally on the
     * client side to incorporate the server response into the
     * request.
     *
     * @param returnValues return values
     **/
    void returnValues(Values returnValues) {
        this.returnValues = returnValues;
    }

    /**
     * Obtain the return values
     *
     * @return request return values
     **/
    public Values returnValues() {
        return returnValues;
    }

    /**
     * Create a new empty set of parameters for this request. The old
     * set of parameters will still be valid, but will no longer be
     * part of this request. This method may be used to allow earlier
     * garbage collection of large parameters that are no longer
     * needed. While the obvious use of this method is to get rid of
     * parameters when being an RPC server it can also be used after
     * starting an RPC request on a client.
     **/
    public void discardParameters() {
        parameters = new Values();
    }

    /**
     * Obtain the Target representing our end of the connection over
     * which this request was invoked. This method may only be invoked
     * during method handling (RPC server aspect).
     *
     * @return Target representing our end of the connection over
     * which this request was invoked
     * @throws IllegalStateException if invoked inappropriately
     **/
    public Target target() {
        if (serverHandler == null) {
            throw new IllegalStateException("No server handler registered");
        }
        return serverHandler.getTarget();
    }

    /**
     * Abort a request. This method may only be called by the RPC
     * client after an asynchronous method invocation was requested.
     *
     * @throws IllegalStateException if invoked inappropriately
     **/
    public void abort() {
        if (clientHandler == null) {
            throw new IllegalStateException("No client handler registered");
        }
        clientHandler.handleAbort();
    }

    /**
     * Detach a method invocation. This method may only be invoked
     * during method handling (RPC server aspect). If this method is
     * invoked, the method is not returned when the method handler
     * returns. Instead, the application must invoke the {@link
     * #returnRequest returnRequest} method when it wants the request
     * to be returned.
     *
     * @throws IllegalStateException if invoked inappropriately
     **/
    public void detach() {
        if (serverHandler == null) {
            throw new IllegalStateException("No server handler registered");
        }
        detached = true;
    }

    /**
     * Check whether this method was detached.
     *
     * @return true if this method was detached
     **/
    boolean isDetached() {
        return detached;
    }

    /**
     * Return this request. This method may only be invoked after the
     * {@link #detach detach} method has been invoked, and only once
     * per request. Note that if you detach a method without invoking
     * this method, it will never be returned, causing a resource leak
     * (NB: not good).
     *
     * @throws IllegalStateException if invoked inappropriately
     **/
    public void returnRequest() {
        if (!detached) {
            throw new IllegalStateException("Request not detached");
        }
        serverHandler.returnRequest();
    }

    /**
     * Register the fact that an error has occurred.
     *
     * @param errorCode the error code (see {@link ErrorCode})
     * @param errorMessage the error message
     **/
    public void setError(int errorCode, String errorMessage) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    /**
     * Check if this Request contains return types compatible with the
     * given type string. If this Request contains an error it is
     * considered incompatible with all possible type strings. If the
     * return values are not compatible with the given type string and
     * an error condition is not set, the {@link
     * ErrorCode#WRONG_RETURN} error will be set. This method is
     * intended to be used by the RPC client after a method has been
     * invoked to verify the return value types. Please refer to the
     * {@link Method} class description for an explanation of type
     * strings.
     *
     * @return true if all is ok and the return types are compatible
     * with 'returnTypes'
     * @param returnTypes type string
     **/
    public boolean checkReturnTypes(String returnTypes) {
        if (errorCode != ErrorCode.NONE) {
            return false;
        }
        if (returnValues.satisfies(returnTypes)) {
            return true;
        }
        setError(ErrorCode.WRONG_RETURN, "checkReturnValues: Wrong return values");
        return false;
    }

    /**
     * Check if an error has occurred with this Request
     *
     * @return true if an error has occurred
     **/
    public boolean isError() {
        return (errorCode != ErrorCode.NONE);
    }

    /**
     * Obtain the error code associated with this Request
     *
     * @return error code
     **/
    public int errorCode() {
        return errorCode;
    }

    /**
     * Obtain the error message associated with this Request, if any
     *
     * @return error message
     **/
    public String errorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return "request " + methodName + "(" + parameters + ")" + ( returnValues.size()>0 ? ": " + returnValues : "");
    }

}
