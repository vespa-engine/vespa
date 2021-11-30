// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.CfgConfigPayloadBuilder;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.PayloadChecksums;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Subscription to use when config id is jar:.../foo.jar[!/pathInJar/]
 *
 * @author Vegard Havdal
 * @author gjoranv
 */
public class JarConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    private final String jarName;
    private final String path;
    private ZipEntry zipEntry = null;

    // jar:configs/app.jar!/configs/
    JarConfigSubscription(ConfigKey<T> key, String jarName, String path) {
        super(key);
        this.jarName = jarName;
        this.path = path;
    }

    @Override
    public boolean nextConfig(long timeout) {
        if (checkReloaded()) {
            // Not supporting changing the payload for jar
            return true;
        }
        if (zipEntry == null) {
            // First time polled
            JarFile jarFile;
            try {
                jarFile = new JarFile(jarName);
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
            zipEntry = getEntry(jarFile, path);
            if (zipEntry == null)
                throw new IllegalArgumentException("Config '" + key.getName() + "' not found in '" + jarName + "!/" + path + "'.");
            T config;
            try {
                ConfigPayload payload = new CfgConfigPayloadBuilder().deserialize(Arrays.asList(IOUtils.readAll(new InputStreamReader(jarFile.getInputStream(zipEntry), StandardCharsets.UTF_8)).split("\n")));
                config = payload.toInstance(configClass, key.getConfigId());
            } catch (UnsupportedEncodingException e) {
                throw new IllegalStateException(e);
            } catch (IOException e) {
                throw new ConfigurationRuntimeException(e);
            }
            setConfig(0L, false, config, PayloadChecksums.empty());
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
        return false;
    }

    /**
     * Returns the entry corresponding to the ConfigInstance's defName in the given directory in
     * the given jar file.
     * The file's existence is checked elsewhere.
     */
    private ZipEntry getEntry(JarFile jarFile, String dir) {
        if (!dir.endsWith("/")) {
            dir = dir + '/';
        }
        return jarFile.getEntry(dir + getConfigFilename(key));
    }

    @Override
    public boolean subscribe(long timeout) {
        return true;
    }

}
