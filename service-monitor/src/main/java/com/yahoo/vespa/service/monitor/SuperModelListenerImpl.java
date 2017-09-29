// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.model.api.SuperModel;
import com.yahoo.config.model.api.SuperModelListener;
import com.yahoo.config.model.api.SuperModelProvider;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;

import java.util.List;
import java.util.logging.Logger;

public class SuperModelListenerImpl implements SuperModelListener {
    private static final Logger logger = Logger.getLogger(SuperModelListenerImpl.class.getName());

    // Guard for updating superModel and slobrokMonitor exclusively and atomically:
    //  - superModel and slobrokMonitor must be updated in combination (exclusively and atomically)
    //  - Anyone may take a snapshot of superModel for reading purposes, hence volatile.
    private final Object monitor = new Object();
    private final SlobrokMonitor2 slobrokMonitor;
    private volatile SuperModel superModel;

    SuperModelListenerImpl(SlobrokMonitor2 slobrokMonitor) {
        this.slobrokMonitor = slobrokMonitor;
    }

    void start(SuperModelProvider superModelProvider) {
        synchronized (monitor) {
            // This snapshot() call needs to be within the synchronized block,
            // since applicationActivated()/applicationRemoved() may be called
            // asynchronously even before snapshot() returns.
            SuperModel snapshot = superModelProvider.snapshot(this);
            exclusiveUpdate(snapshot);
        }
    }

    @Override
    public void applicationActivated(SuperModel superModel, ApplicationId applicationId) {
        synchronized (monitor) {
            exclusiveUpdate(superModel);
        }
    }

    @Override
    public void applicationRemoved(SuperModel superModel, ApplicationId id) {
        synchronized (monitor) {
            exclusiveUpdate(superModel);
        }
    }

    ServiceModel createServiceModelSnapshot(Zone zone, List<String> configServerHostnames) {
        // Save a snapshot of volatile this.superModel outside of synchronized block.
        SuperModel superModelSnapshot = this.superModel;

        ModelGenerator modelGenerator = new ModelGenerator();
        return modelGenerator.toServiceModel(
                superModelSnapshot,
                zone,
                configServerHostnames,
                slobrokMonitor);
    }

    private void exclusiveUpdate(SuperModel superModel) {
        this.superModel = superModel;
        slobrokMonitor.updateSlobrokList(superModel);
    }
}