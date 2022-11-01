// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.CfgConfigPayloadBuilder;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;

import java.io.IOException;

import static java.util.logging.Level.FINE;

/**
 * Subscription used when config id is file:...
 *
 * @author Vegard Havdal
 */
public class FileConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    final FileSource file;
    long ts;

    FileConfigSubscription(ConfigKey<T> key, FileSource file) {
        super(key);
        file.validateFile();
        setGeneration(0L);
        this.file = file;
    }

    @Override
    public boolean nextConfig(long timeout) {
        file.validateFile();
        if (checkReloaded()) {
            log.log(FINE, () -> "User forced config reload at " + System.currentTimeMillis());
            // User forced reload
            setConfigIfChanged(updateConfig());
            ConfigState<T> configState = getConfigState();
            log.log(FINE, () -> "Config updated at " + System.currentTimeMillis() + ", changed: " + configState.isConfigChanged());
            log.log(FINE, () -> "Config: " + configState.getConfig().toString());
            return true;
        }
        if (file.getLastModified() != ts) {
            setConfigIncGen(updateConfig());
            return true;
        }
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            throw new ConfigInterruptedException(e);
        }

        return false;
    }

    private T updateConfig() {
        ts = file.getLastModified();
        try {
            ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(file.getContent());
            return payload.toInstance(configClass, key.getConfigId());
        } catch (IOException e) {
            throw new ConfigurationRuntimeException(e);
        }
    }

    @Override
    public boolean subscribe(long timeout) {
        return true;
    }

}
