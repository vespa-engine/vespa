// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.monitor;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.component.AbstractComponent;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * This class can be used as a component, and will regularly dump known slobrok entries to the log. It will use
 * the slobrok service configured for the current application.
 *
 * It is quite noisy and not useful for anything but testing.
 *
 * @author bakksjo
 */
public class SingleClusterServiceMonitor extends AbstractComponent {
    private static final Logger logger = Logger.getLogger(SingleClusterServiceMonitor.class.getName());

    private volatile JavaSlobrokMonitor slobrokMonitor;

    public SingleClusterServiceMonitor(final SlobroksConfig slobroksConfig) {
        this.slobrokMonitor = new JavaSlobrokMonitor(getSlobrokSpecs(slobroksConfig));

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                final JavaSlobrokMonitor slobrok = slobrokMonitor;
                if (slobrok == null) {
                    return;
                }
                final Map<String, String> services;
                try {
                    services = slobrok.getRegisteredServices();
                } catch (JavaSlobrokMonitor.ServiceTemporarilyUnavailableException e) {
                    logger.info("Slobrok monitor temporarily unavailable");
                    continue;
                }
                if (services.isEmpty()) {
                    logger.info("Slobrok lookup returned no entries");
                }
                services.forEach((serviceName, serviceSpec) ->
                        logger.info("Slobrok entry: " + serviceName + " => " + serviceSpec));
            }
        }).start();
    }

    @Override
    public void deconstruct() {
        final JavaSlobrokMonitor slobrok = slobrokMonitor;
        slobrokMonitor = null;
        // Nothing prevents the mirror from being in use while we are shutting down, but so be it (for now at least).
        slobrok.shutdown();
    }

    private static List<String> getSlobrokSpecs(final SlobroksConfig slobroksConfig) {
        return slobroksConfig.slobrok().stream()
                .map(SlobroksConfig.Slobrok::connectionspec)
                .collect(Collectors.toList());
    }
}
