// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import com.yahoo.foo.AppConfig;
import com.yahoo.vespa.config.TimingValues;

/**
 * @author hmusum
 *
 * Application that subscribes to config defined in app.def and
 * generated code in AppConfig.java.
 */
public class AppService {
    protected int timesConfigured = 0;

    protected AppConfig config = null;
    private final ConfigSubscriber subscriber;
    protected final String configId;

    final Thread configThread;
    boolean stopThread = false;

    public AppService(String configId, ConfigSourceSet csource) {
        this(configId, csource, null);
    }

    public int timesConfigured() { return timesConfigured; }

    public AppService(String configId, ConfigSourceSet csource, TimingValues timingValues) {
        if (csource == null) throw new IllegalArgumentException("Config source cannot be null");
        this.configId = configId;
        subscriber = new ConfigSubscriber(csource);
        ConfigHandle<AppConfig> temp;
        if (timingValues == null) {
            temp = subscriber.subscribe(AppConfig.class, configId);
        } else {
            temp = subscriber.subscribe(AppConfig.class, configId, csource, timingValues);
        }
        final ConfigHandle<AppConfig> handle = temp;
        configThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stopThread) {
                    boolean changed = subscriber.nextConfig(500);
                    if (changed) {
                        configure(handle.getConfig());
                        timesConfigured++;
                    }
                }
            }
        });
        subscriber.nextConfig(5000);
        timesConfigured++;
        configure(handle.getConfig());
        configThread.setDaemon(true);
        configThread.start();
    }

    public void configure(AppConfig config) {
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
