// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.impl.GenericConfigHandle;
import com.yahoo.config.subscription.impl.GenericConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.yolean.Exceptions;

import java.util.Map;
import java.util.logging.Logger;

/**
 * @author hmusum
 */
public class UpstreamConfigSubscriber implements Subscriber {

    private final static Logger log = Logger.getLogger(UpstreamConfigSubscriber.class.getName());

    private final RawConfig config;
    private final ConfigSourceClient configSourceClient;
    private final ConfigSourceSet configSourceSet;
    private final TimingValues timingValues;
    private final JRTConfigRequester requester;
    private final MemoryCache memoryCache;
    private GenericConfigSubscriber subscriber;
    private GenericConfigHandle handle;

    UpstreamConfigSubscriber(RawConfig config, ConfigSourceClient configSourceClient, ConfigSourceSet configSourceSet,
                             TimingValues timingValues, JRTConfigRequester requester,
                             MemoryCache memoryCache) {
        this.config = config;
        this.configSourceClient = configSourceClient;
        this.configSourceSet = configSourceSet;
        this.timingValues = timingValues;
        this.requester = requester;
        this.memoryCache = memoryCache;
    }

    void subscribe() {
        subscriber = new GenericConfigSubscriber(Map.of(configSourceSet, requester));
        ConfigKey<?> key = config.getKey();
        handle = subscriber.subscribe(new ConfigKey<>(key.getName(), key.getConfigId(), key.getNamespace()),
                                      config.getDefContent(), configSourceSet, timingValues);
    }

    @Override
    public void run() {
        do {
            if (! subscriber.nextGeneration()) continue;

            try {
                updateWithNewConfig(handle);
            } catch (Exception e) {  // To avoid thread throwing exception and loop never running this again
                log.log(LogLevel.WARNING, "Got exception: " + Exceptions.toMessageString(e));
            } catch (Throwable e) {
                com.yahoo.protect.Process.logAndDie("Got error, exiting: " + Exceptions.toMessageString(e));
            }
        } while (!subscriber.isClosed());
    }

    private void updateWithNewConfig(GenericConfigHandle handle) {
        RawConfig newConfig = handle.getRawConfig();
        log.log(LogLevel.DEBUG, () -> "config to be returned for '" + newConfig.getKey() +
                "', generation=" + newConfig.getGeneration() +
                ", payload=" + newConfig.getPayload());
        memoryCache.update(newConfig);
        configSourceClient.updateSubscribers(newConfig);
    }

    @Override
    public void cancel() {
        if (subscriber != null) {
            subscriber.close();
        }
    }

}
