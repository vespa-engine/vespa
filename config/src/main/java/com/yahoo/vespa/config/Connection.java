// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;

import java.time.Duration;

/**
 * @author hmusum
 */
public interface Connection {

    void invokeAsync(Request request, Duration jrtTimeout, RequestWaiter requestWaiter);

    void invokeSync(Request request, Duration jrtTimeout);

    String getAddress();

}
