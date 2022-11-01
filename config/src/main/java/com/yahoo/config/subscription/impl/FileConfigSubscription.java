// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.CfgConfigPayloadBuilder;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksums;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static java.util.logging.Level.FINE;

/**
 * Subscription used when config id is file:...
 *
 * @author Vegard Havdal
 */
public class FileConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    final FileSource file;
    long generation = -1;

    FileConfigSubscription(ConfigKey<T> key, FileSource file) {
        super(key);
        if ( ! file.getFile().isFile()) throw new IllegalArgumentException("Not a file: " + file);

        this.file = file;
    }

    @Override
    public boolean nextConfig(long timeout) {
        if ( ! file.getFile().isFile()) throw new IllegalArgumentException("Not a file: " + file);
        if (generation != (generation = file.generation())) {
            setConfigAndGeneration(generation, false, updateConfig(), PayloadChecksums.empty());
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
        try {
            ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(Files.readAllLines(file.getFile().toPath()));
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
