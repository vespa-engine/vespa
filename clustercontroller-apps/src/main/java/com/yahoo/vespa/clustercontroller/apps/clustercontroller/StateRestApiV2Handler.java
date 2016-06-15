// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ModelConfig;
import com.yahoo.container.jdisc.config.HttpServerConfig;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.clustercontroller.apputil.communication.http.JDiscHttpRequestHandler;
import com.yahoo.vespa.clustercontroller.core.restapiv2.ClusterControllerStateRestAPI;
import com.yahoo.vespa.clustercontroller.utils.staterestapi.server.RestApiHandler;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class StateRestApiV2Handler extends JDiscHttpRequestHandler {
    private static final Logger log = Logger.getLogger(StateRestApiV2Handler.class.getName());

    @Inject
    public StateRestApiV2Handler(Executor executor, ClusterController cc, ClusterInfoConfig config, AccessLog accessLog) {
        this(executor, new ClusterControllerStateRestAPI(cc, getClusterControllerSockets(config)), "/cluster/v2", accessLog);
    }

    private StateRestApiV2Handler(Executor executor, ClusterControllerStateRestAPI restApi, String pathPrefix, AccessLog accessLog) {
        super(new RestApiHandler(restApi).setDefaultPathPrefix(pathPrefix), executor, accessLog);
    }

    // This method is package-private instead of private to be accessible to unit-tests.
    static Map<Integer, ClusterControllerStateRestAPI.Socket> getClusterControllerSockets(ClusterInfoConfig config) {
        Map<Integer, ClusterControllerStateRestAPI.Socket> result = new TreeMap<>();
        for (ClusterInfoConfig.Services service : config.services()) {
            for (ClusterInfoConfig.Services.Ports port : service.ports()) {
                Set<String> tags = parseTags(port.tags());
                if (tags.contains("http") && tags.contains("state")) {
                    result.put(service.index(), new ClusterControllerStateRestAPI.Socket(service.hostname(), port.number()));
                    break;
                }
            }
        }
        if (result.isEmpty()) {
            log.warning("Found no cluster controller in model config");
        } else if (log.isLoggable(LogLevel.DEBUG)) {
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(result.size()).append(" cluster controllers in model config:");
            for (Map.Entry<Integer, ClusterControllerStateRestAPI.Socket> e : result.entrySet()) {
                sb.append("\n  ").append(e.getKey()).append(" -> ").append(e.getValue());
            }
            log.fine(sb.toString());
        }
        return result;
    }

    private static Set<String> parseTags(String tags) {
        Set<String> set = new HashSet<>();
        for(String s : tags.toLowerCase().split(" ")) {
            set.add(s.trim());
        }
        return set;
    }
}
