// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.AppConfig;
import com.yahoo.vespa.config.TimingValues;

/**
 * @author hmusum
 *
 * Application that subscribes to config defined in app.def and
 * generated code in AppConfig.java.
 */
class AppService {

    private int timesConfigured = 0;

    private AppConfig config = null;
    private final ConfigSubscriber subscriber;

    private boolean stopThread = false;

    AppService(String configId, ConfigSourceSet csource) {
        this(configId, csource, null);
    }

    int timesConfigured() { return timesConfigured; }

    private AppService(String configId, ConfigSourceSet csource, TimingValues timingValues) {
        if (csource == null) throw new IllegalArgumentException("Config source cannot be null");
        subscriber = new ConfigSubscriber(csource);
        ConfigHandle<AppConfig> temp;
        if (timingValues == null) {
            temp = subscriber.subscribe(AppConfig.class, configId);
        } else {
            temp = subscriber.subscribe(AppConfig.class, configId, csource, timingValues);
        }
        ConfigHandle<AppConfig> handle = temp;
        Thread configThread = new Thread(() -> {
            while (!stopThread) {
                boolean changed = subscriber.nextConfig(500, false);
                if (changed) {
                    configure(handle.getConfig());
                    timesConfigured++;
                }
            }
        });
        subscriber.nextConfig(5000, false);
        timesConfigured++;
        configure(handle.getConfig());
        configThread.setDaemon(true);
        configThread.start();
    }

    private void configure(AppConfig config) {
        this.config = config;
    }

    public void cancelSubscription() {
        subscriber.close();
        stopThread = true;
    }

    public AppConfig getConfig() {
        return config;
    }

    public boolean isConfigured() {
        return (timesConfigured > 0);
    }

}
