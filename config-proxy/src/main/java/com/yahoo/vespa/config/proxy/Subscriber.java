// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.impl.GenericConfigHandle;
import com.yahoo.config.subscription.impl.GenericConfigSubscriber;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.yolean.Exceptions;

import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author hmusum
 */
public class Subscriber {

    private final static Logger log = Logger.getLogger(Subscriber.class.getName());

    private final RawConfig config;
    private final TimingValues timingValues;
    private final GenericConfigSubscriber subscriber;
    private GenericConfigHandle handle;

    Subscriber(RawConfig config, TimingValues timingValues, JRTConfigRequester requester) {
        this.config = config;
        this.timingValues = timingValues;
        this.subscriber = new GenericConfigSubscriber(requester);
    }

    void subscribe() {
        ConfigKey<?> key = config.getKey();
        handle = subscriber.subscribe(new ConfigKey<>(key.getName(), key.getConfigId(), key.getNamespace()),
                                      config.getDefContent(), timingValues);
    }

    public Optional<RawConfig> nextGeneration() {
        try {
            // 'isInitializing' argument to nextGeneration() is true, config proxy should never skip config due to not initializing
            if (subscriber.nextGeneration(0, true)) {
                RawConfig rawConfig = handle.getRawConfig();
                if (rawConfig == null)
                    log.log(Level.SEVERE, "Config for " + config.getKey() + " is null");
                return Optional.ofNullable(rawConfig);
            }
        } catch (Exception e) {  // To avoid thread throwing exception and loop never running this again
            log.log(Level.WARNING, "Got exception: " + Exceptions.toMessageString(e));
        } catch (Throwable e) {
            com.yahoo.protect.Process.logAndDie("Got error, exiting: " + Exceptions.toMessageString(e));
        }
        return Optional.empty();
    }

    public void cancel() { subscriber.close(); }

    boolean isClosed() { return subscriber.isClosed(); }

}
