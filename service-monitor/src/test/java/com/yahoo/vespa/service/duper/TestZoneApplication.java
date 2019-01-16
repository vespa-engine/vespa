// Copyright 2019 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.model.api.ApplicationInfo;
import com.yahoo.config.model.api.HostInfo;
import com.yahoo.config.provision.HostName;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author hakonhall
 */
public class TestZoneApplication {

    private final List<HostName> nodeAdminHostnames;
    private final List<HostName> routingHostnames;

    private TestZoneApplication(List<HostName> nodeAdminHostnames, List<HostName> routingHostnames) {
        this.nodeAdminHostnames = nodeAdminHostnames;
        this.routingHostnames = routingHostnames;
    }

    public ApplicationInfo makeApplicationInfo() {
        // Make a test ApplicationInfo by:
        //  1. Make an ApplicationInfo as-if the node-admin cluster of the zone application were the only cluster.
        //     Make sure to get the correct tenant name, application name, cluster id, service type, hostnames,
        //     services, and ports. This should be easy with the help of InfraApplication.
        ApplicationInfo nodeAdminPart = new NodeAdminPartOfZoneApplication().makeApplicationInfo(nodeAdminHostnames);

        //  2. Make an ApplicationInfo as-if the routing cluster of the zone application were the only cluster.
        //     Don't care if the application is not perfect.
        ApplicationInfo routingPart = new RoutingPartOfZoneApplication().makeApplicationInfo(routingHostnames);

        //  3. Take HostInfo from (1) and (2) to make a single ApplicationInfo.
        List<HostInfo> allHostInfos = new ArrayList<>();
        allHostInfos.addAll(nodeAdminPart.getModel().getHosts());
        allHostInfos.addAll(routingPart.getModel().getHosts());

        return new ApplicationInfo(nodeAdminPart.getApplicationId(), 0, new HostsModel(allHostInfos));
    }

    public static class Builder {
        private List<HostName> nodeAdminHostnames = null;
        private List<HostName> routingHostnames = null;

        public Builder addNodeAdminCluster(String... hostnames) {
            this.nodeAdminHostnames = Stream.of(hostnames).map(HostName::from).collect(Collectors.toList());
            return this;
        }

        public Builder addRoutingCluster(String... hostnames) {
            this.routingHostnames = Stream.of(hostnames).map(HostName::from).collect(Collectors.toList());
            return this;
        }

        public TestZoneApplication build() {
            return new TestZoneApplication(Objects.requireNonNull(nodeAdminHostnames), Objects.requireNonNull(routingHostnames));
        }
    }

    private static class NodeAdminPartOfZoneApplication extends InfraApplication {
        public NodeAdminPartOfZoneApplication() {
            super(ZoneApplication.getApplicationName().value(),
                    ZoneApplication.getNodeAdminNodeType(),
                    ZoneApplication.getNodeAdminClusterSpecType(),
                    ZoneApplication.getNodeAdminClusterSpecId(),
                    ZoneApplication.getNodeAdminServiceType(),
                    ZoneApplication.getNodeAdminHealthPort());
        }
    }

    /**
     * This InfraApplication is bogus (containing host admin instead of jdisc container), but the tests are
     * not supposed to explore this cluster.
     */
    private static class RoutingPartOfZoneApplication extends InfraApplication {
        public RoutingPartOfZoneApplication() {
            super(ZoneApplication.getApplicationName().value(),
                    ZoneApplication.getRoutingNodeType(),
                    ZoneApplication.getRoutingClusterSpecType(),
                    ZoneApplication.getRoutingClusterSpecId(),
                    ZoneApplication.getRoutingServiceType(),
                    ZoneApplication.getRoutingHealthPort());
        }
    }
}
