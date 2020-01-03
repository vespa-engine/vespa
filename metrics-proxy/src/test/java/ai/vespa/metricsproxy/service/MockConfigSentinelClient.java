// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import com.yahoo.log.LogLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Mock config sentinel
 *
 * @author hmusum
 */
public class MockConfigSentinelClient extends ConfigSentinelClient {
    private final ConfigSentinelDummy configSentinel;
    private final static Logger log = Logger.getLogger(MockConfigSentinelClient.class.getPackage().getName());

    public MockConfigSentinelClient(ConfigSentinelDummy configSentinel) {
        super();
        this.configSentinel = configSentinel;
    }

    @Override
    protected synchronized void setStatus(List<VespaService> services) throws Exception {
        List<VespaService> updatedServices = new ArrayList<>();
        String[] lines = configSentinel.getServiceList().split("\n");
        for (String line : lines) {
            if (line.equals("")) {
                break;
            }

            VespaService s = parseServiceString(line, services);
            if (s != null) {
                updatedServices.add(s);
            }
        }

        //Check if there are services that were not found in
        //from the sentinel
        for (VespaService s : services) {
            if (!updatedServices.contains(s)) {
                log.log(LogLevel.DEBUG, "Service " + s + " is no longer found with sentinel - setting alive = false");
                s.setAlive(false);
            }
        }
    }
}
