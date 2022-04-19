// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.curator.Lock;

/**
 * @author jonmv
 */
public class LockedStep {

    private final Step step;
    LockedStep(Mutex lock, Step step) { this.step = step; }
    public Step get() { return step; }

}
