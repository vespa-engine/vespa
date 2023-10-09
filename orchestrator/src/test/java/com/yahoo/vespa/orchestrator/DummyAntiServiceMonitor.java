// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator;

import com.yahoo.vespa.service.monitor.AntiServiceMonitor;
import com.yahoo.vespa.service.monitor.CriticalRegion;

/**
 * @author hakonhall
 */
public class DummyAntiServiceMonitor implements AntiServiceMonitor {
    @Override
    public CriticalRegion disallowDuperModelLockAcquisition(String regionDescription) {
        return () -> {};
    }
}
