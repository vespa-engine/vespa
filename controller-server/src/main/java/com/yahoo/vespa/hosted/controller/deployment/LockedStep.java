package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.curator.Lock;

public class LockedStep {

    private final Step step;
    LockedStep(Lock lock, Step step) { this.step = step; }
    public Step get() { return step; }

}
