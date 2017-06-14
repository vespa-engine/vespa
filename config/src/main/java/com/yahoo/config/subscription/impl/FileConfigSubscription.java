// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.CfgConfigPayloadBuilder;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.log.LogLevel;

/**
 * Subscription used when config id is file:...
 * @author vegardh
 * @since 5.1
 *
 */
public class FileConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    final File file;
    long ts;

    FileConfigSubscription(ConfigKey<T> key, ConfigSubscriber subscriber, File f) {
        super(key, subscriber);
        setGeneration(0L);
        file=f;
        if (!file.exists() && !file.isFile())
            throw new IllegalArgumentException("Not a file: "+file);
    }

    @Override
    public boolean nextConfig(long timeout) {
        if (!file.exists() && !file.isFile()) throw new IllegalArgumentException("Not a file: "+file);
        if (checkReloaded()) {
            log.log(LogLevel.DEBUG, "User forced config reload at " + System.currentTimeMillis());
            // User forced reload
            updateConfig();
            log.log(LogLevel.DEBUG, "Config updated at " + System.currentTimeMillis() + ", changed: " + isConfigChanged());
            log.log(LogLevel.DEBUG, "Config: " + config.toString());
            return true;
        }
        if (file.lastModified()!=ts) {
            updateConfig();
            generation++;
            setGenerationChanged(true);
            return true;
        }
        try {
            Thread.sleep(timeout);
        } catch (InterruptedException e) {
            throw new ConfigInterruptedException(e);
        }
        // These shouldn't be checked anywhere since we return false now, but setting them still
        setGenerationChanged(false);
        setConfigChanged(false);
        return false;
    }

    private void updateConfig() {
        ts=file.lastModified();
        ConfigInstance prev = config;
        try {
            ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(Arrays.asList(IOUtils.readFile(file).split("\n")));
            config = payload.toInstance(configClass, key.getConfigId());
        } catch (IOException e) {
            throw new ConfigurationRuntimeException(e);
        }
        setConfigChanged(!config.equals(prev));
    }

    @Override
    public boolean subscribe(long timeout) {
        return true;
    }

}
