// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.impl.GenericConfigHandle;
import com.yahoo.config.subscription.impl.GenericConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.log.LogLevel;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.ConfigCacheKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * @author musum
 * @since 5.5
 */
public class UpstreamConfigSubscriber implements Subscriber {
    private final static Logger log = Logger.getLogger(UpstreamConfigSubscriber.class.getName());

    private final RawConfig config;
    private final ClientUpdater clientUpdater;
    private final ConfigSource configSourceSet;
    private final TimingValues timingValues;
    private Map<ConfigSourceSet, JRTConfigRequester> requesterPool;
    private final Map<ConfigCacheKey, Subscriber> activeSubscribers;
    private GenericConfigSubscriber subscriber;

    public UpstreamConfigSubscriber(RawConfig config,
                                    ClientUpdater clientUpdater,
                                    ConfigSource configSourceSet,
                                    TimingValues timingValues,
                                    Map<ConfigSourceSet, JRTConfigRequester> requesterPool,
                                    Map<ConfigCacheKey, Subscriber> activeSubscribers) {
        this.config = config;
        this.clientUpdater = clientUpdater;
        this.configSourceSet = configSourceSet;
        this.timingValues = timingValues;
        this.requesterPool = requesterPool;
        this.activeSubscribers = activeSubscribers;
    }

    UpstreamConfigSubscriber(RawConfig config,
                             ClientUpdater clientUpdater,
                             ConfigSource configSourceSet,
                             TimingValues timingValues,
                             Map<ConfigSourceSet, JRTConfigRequester> requesterPool) {
        this(config, clientUpdater, configSourceSet, timingValues, requesterPool, new HashMap<>());
    }

    @Override
    public void run() {
        GenericConfigHandle handle;
        subscriber = new GenericConfigSubscriber(requesterPool);
        try {
            handle = subscriber.subscribe(config.getKey(), config.getDefContent(), configSourceSet, timingValues);
        } catch (ConfigurationRuntimeException e) {
            log.log(LogLevel.INFO, "Subscribe for '" + config + "' failed, closing subscriber");
            final ConfigCacheKey key = new ConfigCacheKey(config.getKey(), config.getDefMd5());
            synchronized (activeSubscribers) {
                final Subscriber activeSubscriber = activeSubscribers.get(key);
                if (activeSubscriber != null) {
                    activeSubscriber.cancel();
                    activeSubscribers.remove(key);
                }
            }
            return;
        }

        do {
            try {
                if (subscriber.nextGeneration()) {
                    if (log.isLoggable(LogLevel.DEBUG)) {
                        log.log(LogLevel.DEBUG, "nextGeneration returned for " + config.getKey() + ", subscriber generation=" + subscriber.getGeneration());
                    }
                    updateWithNewConfig(handle);
                }
            } catch (Exception e) {  // To avoid thread throwing exception and loop never running this again
                log.log(LogLevel.WARNING, "Got exception: " + Exceptions.toMessageString(e));
            } catch (Throwable e) {
                com.yahoo.protect.Process.logAndDie("Got error, exiting: " + Exceptions.toMessageString(e));
            }
        } while (!subscriber.isClosed());
    }

    private void updateWithNewConfig(GenericConfigHandle handle) {
        final RawConfig newConfig = handle.getRawConfig();
        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "config to be returned for '" + newConfig.getKey() +
                    "', generation=" + newConfig.getGeneration() +
                    ", payload=" + newConfig.getPayload());
        }
        clientUpdater.updateSubscribers(newConfig);
    }

    @Override
    public void cancel() {
        if (subscriber != null) {
            subscriber.close();
        }
    }
}
