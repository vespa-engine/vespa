// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.CfgConfigPayloadBuilder;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;

/**
 * Subscription to use when config id is jar:.../foo.jar[!/pathInJar/]
 *
 * @author vegardh
 * @author gjoranv
 * @since 5.1
 *
 */
public class JarConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {
    private final String jarName;
    private final String path;
    private ZipEntry zipEntry = null;

    // jar:configs/app.jar!/configs/
    JarConfigSubscription(ConfigKey<T> key, ConfigSubscriber subscriber, String jarName, String path) {
        super(key, subscriber);
        this.jarName=jarName;
        this.path=path;
    }

    @Override
    public boolean nextConfig(long timeout) {
        if (checkReloaded()) {
            // Not supporting changing the payload for jar
            return true;
        }
        if (zipEntry==null) {
            // First time polled
            JarFile jarFile = null;
            try {
                jarFile = new JarFile(jarName);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
            zipEntry = getEntry(jarFile, path);
            if (zipEntry==null) throw new IllegalArgumentException("Config '" + key.getName() + "' not found in '" + jarName + "!/" + path + "'.");
            try {
                ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(Arrays.asList(IOUtils.readAll(new InputStreamReader(jarFile.getInputStream(zipEntry), "UTF-8")).split("\n")));
                config = payload.toInstance(configClass, key.getConfigId());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            } catch (IOException e) {
                throw new ConfigurationRuntimeException(e);
            }
            setGeneration(0L);
            setGenerationChanged(true);
            setConfigChanged(true);
            try {
                jarFile.close();
            } catch (IOException e) {
                throw new ConfigurationRuntimeException(e);
            }
            return true;
        }
        // TODO: Should wait and detect changes
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
    /**
     * Returns the entry corresponding to the ConfigInstance's defName/Version in the given directory in
     * the given JarFile.
     * If the file with correct version number does not exist, returns the filename without version number.
     * The file's existence is checked elsewhere.
     */
    private ZipEntry getEntry(JarFile jarFile, String dir) {
        if (!dir.endsWith("/")) {
            dir = dir + '/';
        }
        return jarFile.getEntry(dir + getConfigFilenameNoVersion(key));
    }

    @Override
    public boolean subscribe(long timeout) {
        return true;
    }

}
