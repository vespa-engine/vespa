// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.impl.GenericConfigHandle;
import com.yahoo.config.subscription.impl.GenericConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigRequester;

import java.util.Optional;
import java.util.logging.Level;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.yolean.Exceptions;

import java.util.Map;
import java.util.logging.Logger;

/**
 * @author hmusum
 */
public class Subscriber {

    private final static Logger log = Logger.getLogger(Subscriber.class.getName());

    private final RawConfig config;
    private final ConfigSourceSet configSourceSet;
    private final TimingValues timingValues;
    private final GenericConfigSubscriber subscriber;
    private GenericConfigHandle handle;

    Subscriber(RawConfig config, ConfigSourceSet configSourceSet, TimingValues timingValues, JRTConfigRequester requester) {
        this.config = config;
        this.configSourceSet = configSourceSet;
        this.timingValues = timingValues;
        this.subscriber = new GenericConfigSubscriber(Map.of(configSourceSet, requester));
    }

    void subscribe() {
        ConfigKey<?> key = config.getKey();
        handle = subscriber.subscribe(new ConfigKey<>(key.getName(), key.getConfigId(), key.getNamespace()),
                                      config.getDefContent(), configSourceSet, timingValues);
    }

    public Optional<RawConfig> nextGeneration() {
        if (subscriber.nextGeneration(0, true)) { // Proxy should never skip config due to not initializing
            try {
                return Optional.of(handle.getRawConfig());
            } catch (Exception e) {  // To avoid thread throwing exception and loop never running this again
                log.log(Level.WARNING, "Got exception: " + Exceptions.toMessageString(e));
            } catch (Throwable e) {
                com.yahoo.protect.Process.logAndDie("Got error, exiting: " + Exceptions.toMessageString(e));
            }
        }
        return Optional.empty();
    }

    public void cancel() {
        if (subscriber != null) {
            subscriber.close();
        }
    }

    boolean isClosed() {
        return subscriber.isClosed();
    }

}
