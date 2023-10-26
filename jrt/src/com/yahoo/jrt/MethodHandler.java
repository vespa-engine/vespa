// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt;


/**
 * <p>Interface used to handle the invocation of a method.</p>
 *
 * <p>The {@link Method} class is used to register rpc methods. There
 * are two ways rpc methods can be defined(bound); with this interface
 * or with reflection. This choice is reflected by the two different
 * constructors in the {@link Method} class.</p>
 **/
@FunctionalInterface
public interface MethodHandler {

    /**
     * Method used to dispatch an rpc request.
     *
     * @param req the request
     **/
    public void invoke(Request req);
}
