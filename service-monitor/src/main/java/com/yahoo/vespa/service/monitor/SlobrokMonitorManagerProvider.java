// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.google.inject.Inject;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.vespa.service.monitor.internal.SlobrokMonitorManagerImpl;

public class SlobrokMonitorManagerProvider implements Provider<SlobrokMonitorManager> {
    private final SlobrokMonitorManager slobrokMonitorManager;

    @Inject
    public SlobrokMonitorManagerProvider() {
        slobrokMonitorManager = new SlobrokMonitorManagerImpl();
    }

    @Override
    public SlobrokMonitorManager get() {
        return slobrokMonitorManager;
    }

    @Override
    public void deconstruct() {}
}
