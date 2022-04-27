// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.messagebus.ThrottlePolicy;

/**
 * Superclass of the classes which contains the parameters for creating or opening a session. This is currently empty,
 * but keeping this parameter hierarchy in place means that we can later add parameters with default values that all
 * clients will be able to use with no code changes.
 *
 * @author bratseth
 */
public class Parameters {

    ThrottlePolicy throttlePolicy;

    public void setThrottlePolicy(ThrottlePolicy throttlePolicy) {
        this.throttlePolicy = throttlePolicy;
    }

    public ThrottlePolicy getThrottlePolicy() {
        return throttlePolicy;
    }

}
