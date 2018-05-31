// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import java.util.Arrays;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.CfgConfigPayloadBuilder;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;

/**
 * Subscription used when config id is raw:...
 *
 * Config is the actual text given after the config id, with newlines
 *
 * @author vegardh
 */
public class RawConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    final String inputPayload;
    String payload;

    RawConfigSubscription(ConfigKey<T> key, ConfigSubscriber subscriber, String pl) {
        super(key, subscriber);
        this.inputPayload=pl;
    }

    @Override
    public boolean nextConfig(long timeout) {
        if (checkReloaded()) {
            return true;
        }
        if (payload==null) {
            payload = inputPayload;
            ConfigPayload configPayload = new CfgConfigPayloadBuilder().deserialize(Arrays.asList(payload.split("\n")));
            setConfig(0L, configPayload.toInstance(configClass, key.getConfigId()));
            return true;
        }
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            throw new ConfigInterruptedException(e);
        }
        return false;
    }

    @Override
    public boolean subscribe(long timeout) {
        return true;
    }

}
