// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor.internal.slobrok;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Class to manage Slobrok
 */
public class SlobrokMonitor implements AutoCloseable {
    public static final String SLOBROK_SERVICE_TYPE = "slobrok";
    public static final String SLOBROK_RPC_PORT_TAG = "rpc";

    private final SlobrokList slobrokList;
    private final Mirror mirror;

    SlobrokMonitor() {
        this(new SlobrokList());
    }

    // Package-private for testing.
    SlobrokMonitor(SlobrokList slobrokList, Mirror mirror) {
        this.slobrokList = slobrokList;
        this.mirror = mirror;
    }

    private SlobrokMonitor(SlobrokList slobrokList) {
        this(slobrokList, new Mirror(new Supervisor(new Transport()), slobrokList));
    }

    void updateSlobrokList(ApplicationInfo application) {
        List<String> slobrokSpecs = getSlobrokSpecs(application);
        slobrokList.setup(slobrokSpecs.toArray(new String[0]));
    }

    List<String> getSlobrokSpecs(ApplicationInfo applicationInfo) {
        List<String> slobrokSpecs = new ArrayList<>();

        for (HostInfo host : applicationInfo.getModel().getHosts()) {
            for (ServiceInfo service : host.getServices()) {
                if (!Objects.equals(service.getServiceType(), SLOBROK_SERVICE_TYPE)) {
                    continue;
                }

                for (PortInfo port : service.getPorts()) {
                    if (port.getTags().contains(SLOBROK_RPC_PORT_TAG)) {
                        Spec spec = new Spec(host.getHostname(), port.getPort());
                        slobrokSpecs.add(spec.toString());
                    }
                }
            }
        }

        return slobrokSpecs;
    }

    List<Mirror.Entry> lookup(String pattern) {
        return Arrays.asList(mirror.lookup(pattern));
    }

    @Override
    public void close() {
        mirror.shutdown();
    }

    boolean registeredInSlobrok(String slobrokServiceName) {
        return mirror.lookup(slobrokServiceName).length > 0;
    }
}
