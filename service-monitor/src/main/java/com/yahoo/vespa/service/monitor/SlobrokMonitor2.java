// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.model.api.PortInfo;
import com.yahoo.config.model.api.ServiceInfo;
import com.yahoo.config.model.api.SuperModel;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

public class SlobrokMonitor2 implements AutoCloseable {
    public static final String SLOBROK_RPC_PORT_TAG = "rpc";

    private static final Logger log = Logger.getLogger(SlobrokMonitor2.class.getName());

    private final SlobrokList slobrokList;
    private final Mirror mirror;

    SlobrokMonitor2() {
        this(new SlobrokList());
    }

    // Package-private for testing.
    SlobrokMonitor2(SlobrokList slobrokList, Mirror mirror) {
        this.slobrokList = slobrokList;
        this.mirror = mirror;
    }

    private SlobrokMonitor2(SlobrokList slobrokList) {
        this(slobrokList, new Mirror(new Supervisor(new Transport()), slobrokList));
    }

    void updateSlobrokList(SuperModel superModel) {
        // If we ever need to optimize this method, then we should make this class
        // have a Map<ApplicationId, List<String>>, mapping each application to
        // its list of specs. Then, whenever a single application is activated or removed,
        // only modify that List<String>.

        List<String> slobrokSpecs = new ArrayList<>();

        for (ApplicationInfo application : superModel.getAllApplicationInfos()) {
            for (HostInfo host : application.getModel().getHosts()) {
                for (ServiceInfo service : host.getServices()) {
                    for (PortInfo port : service.getPorts()) {
                        if (port.getTags().contains(SLOBROK_RPC_PORT_TAG)) {
                            Spec spec = new Spec(host.getHostname(), port.getPort());
                            slobrokSpecs.add(spec.toString());
                        }
                    }
                }
            }
        }

        slobrokList.setup(slobrokSpecs.toArray(new String[0]));
    }

    ServiceMonitorStatus getStatus(ServiceType serviceType, ConfigId configId) {
        Optional<String> slobrokServiceName = lookup(serviceType, configId);
        if (slobrokServiceName.isPresent()) {
            if (mirror.lookup(slobrokServiceName.get()).length != 0) {
                return ServiceMonitorStatus.UP;
            } else {
                return ServiceMonitorStatus.DOWN;
            }
        } else {
            return ServiceMonitorStatus.NOT_CHECKED;
        }
    }

    @Override
    public void close() {
        mirror.shutdown();
    }

    // Package-private for testing
    Optional<String> lookup(ServiceType serviceType, ConfigId configId) {
        switch (serviceType.s()) {
            case "adminserver":
            case "config-sentinel":
            case "configproxy":
            case "configserver":
            case "filedistributorservice":
            case "logd":
            case "logserver":
            case "metricsproxy":
            case "slobrok":
            case "transactionlogserver":
                return Optional.empty();

            case "topleveldispatch":
                return Optional.of(configId.s());

            case "qrserver":
            case "container":
            case "docprocservice":
            case "container-clustercontroller":
                return Optional.of("vespa/service/" + configId.s());

            case "searchnode": //TODO: handle only as storagenode instead of both as searchnode/storagenode
                return Optional.of(configId.s() + "/realtimecontroller");
            case "distributor":
            case "storagenode":
                return Optional.of("storage/cluster." + configId.s());
            default:
                log.log(LogLevel.DEBUG, "Unknown service type " + serviceType.s() + " with config id " + configId.s());
                return Optional.empty();
        }
    }
}
