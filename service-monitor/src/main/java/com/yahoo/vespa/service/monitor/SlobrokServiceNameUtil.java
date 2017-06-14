// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.applicationmodel.ConfigId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.monitor.SlobrokMonitor.SlobrokServiceName;
import scala.Option;

import java.util.logging.Logger;

/**
 * @author tonytv
 */
public class SlobrokServiceNameUtil {
    private static final Logger log = Logger.getLogger(SlobrokServiceNameUtil.class.getName());

    private static final String configServerServiceTypeString = "configserver";
    public static final ServiceType configServerServiceType = new ServiceType(configServerServiceTypeString);

    private SlobrokServiceNameUtil() {}

    /**
     * Returns the name a service instance is registered with in slobrok,
     * or empty if the service instance is never registered in slobrok.
     */
    public static Option<SlobrokServiceName> serviceName(ServiceType serviceType, ConfigId configId) {
        switch (serviceType.s()) {
            case "adminserver":
            case "config-sentinel":
            case "configproxy":
            case configServerServiceTypeString:
            case "filedistributorservice":
            case "logd":
            case "logserver":
            case "metricsproxy":
            case "slobrok":
            case "transactionlogserver":
            case "ytracecleaner":
                return Option.empty();

            case "topleveldispatch":
                return Option.apply(new SlobrokServiceName(configId.s()));

            case "qrserver":
            case "container":
            case "docprocservice":
            case "container-clustercontroller":
                return Option.apply(new SlobrokServiceName("vespa/service/" + configId.s()));

            case "searchnode": //TODO: handle only as storagenode instead of both as searchnode/storagenode
                return Option.apply(new SlobrokServiceName(configId.s() + "/realtimecontroller"));
            case "distributor":
            case "storagenode":
                return Option.apply(new SlobrokServiceName("storage/cluster." + configId.s()));
            default:
                log.log(LogLevel.DEBUG, "Unknown service type " + serviceType.s() + " with config id " + configId.s());
                return Option.empty();
        }
    }
}
