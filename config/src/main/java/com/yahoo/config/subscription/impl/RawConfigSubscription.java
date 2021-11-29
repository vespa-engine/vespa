// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.CfgConfigPayloadBuilder;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksums;

import java.util.Arrays;

/**
 * Subscription used when config id is raw:...
 * <p>
 * Config is the actual text given after the config id, with newlines
 *
 * @author Vegard Havdal
 */
public class RawConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    final String inputPayload;
    String payload;

    RawConfigSubscription(ConfigKey<T> key, String payload) {
        super(key);
        this.inputPayload = payload;
    }

    @Override
    public boolean nextConfig(long timeout) {
        if (checkReloaded()) {
            return true;
        }
        if (payload == null) {
            payload = inputPayload;
            ConfigPayload configPayload = new CfgConfigPayloadBuilder().deserialize(Arrays.asList(payload.split("\n")));
            setConfig(0L, false, configPayload.toInstance(configClass, key.getConfigId()), PayloadChecksums.empty());
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
