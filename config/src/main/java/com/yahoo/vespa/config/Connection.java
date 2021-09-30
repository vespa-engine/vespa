// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;

/**
 * @author hmusum
 */
public interface Connection {

    void invokeAsync(Request request, double jrtTimeout, RequestWaiter requestWaiter);

    void invokeSync(Request request, double jrtTimeout);

    String getAddress();

}
