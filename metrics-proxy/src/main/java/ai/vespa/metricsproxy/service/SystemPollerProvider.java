// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.core.MonitoringConfig;
import com.yahoo.container.di.componentgraph.Provider;

/**
 * @author gjoranv
 */
public class SystemPollerProvider implements Provider<SystemPoller> {

    private final SystemPoller poller;

    /**
     * @param services   The list of VespaService instances to monitor for System metrics
     * @param monitoringConfig   The interval in seconds between each polling.
     */
    public SystemPollerProvider (VespaServices services, MonitoringConfig monitoringConfig) {
        if (runningOnLinux()) {
            poller = new SystemPoller(services.getVespaServices(), 60 * monitoringConfig.intervalMinutes());
            poller.poll();
        } else {
            poller = null;
        }
    }

    public void deconstruct() {
        if (poller != null) poller.stop();
    }

    public SystemPoller get() {
        if (poller == null) {
            throw new IllegalStateException("System poller is only available on Linux, current OS is" + getOs());
        }
        return poller;
    }

    public static boolean runningOnLinux() {
        return getOs().contains("nux");
    }

    private static String getOs() {
        return System.getProperty("os.name");
    }
}
