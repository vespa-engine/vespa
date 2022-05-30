// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.FileReference;

import java.nio.file.Path;

import static com.yahoo.vespa.config.ConfigPayloadApplier.IdentityPathAcquirer;

/**
 * A utility class that can be used to transform config from one format to another.
 *
 * @author Ulf Lilleengen
 * @author hmusum
 * @author Tony Vaagenes
 */
public class ConfigTransformer<T extends ConfigInstance> {

    /**
     * Workaround since FileAcquirer is in a separate module that depends on config.
     * Consider moving FileAcquirer into config instead.
     */
    public interface PathAcquirer {
        Path getPath(FileReference fileReference);
    }

    private final Class<T> clazz;

    private static volatile PathAcquirer pathAcquirer = new IdentityPathAcquirer();
    private static volatile UrlDownloader urlDownloader = null;

    /**
     * For internal use only *
     */
    public static void setPathAcquirer(PathAcquirer pathAcquirer) {
        ConfigTransformer.pathAcquirer = (pathAcquirer == null) ?
                new IdentityPathAcquirer() :
                pathAcquirer;
    }

    public static void setUrlDownloader(UrlDownloader urlDownloader) {
        ConfigTransformer.urlDownloader = urlDownloader;
    }

    /**
     * Create a transformer capable of converting payloads to clazz
     *
     * @param clazz a Class for the config instance which this config payload should create a builder for
     */
    public ConfigTransformer(Class<T> clazz) {
        this.clazz = clazz;
    }

    /**
     * Create a ConfigBuilder from a payload, based on the <code>clazz</code> supplied.
     *
     * @param payload a Payload to be transformed to builder
     * @return a ConfigBuilder
     */
    public ConfigInstance.Builder toConfigBuilder(ConfigPayload payload) {
        ConfigInstance.Builder builder = getRootBuilder();
        ConfigPayloadApplier<?> creator = new ConfigPayloadApplier<>(builder, pathAcquirer, urlDownloader);
        creator.applyPayload(payload);
        return builder;
    }

    private ConfigInstance.Builder getRootBuilder() {
        ConfigInstance.Builder builder = null;
        Class<?>[] classes = clazz.getDeclaredClasses();
        for (Class<?> c : classes) {
            if (c.getName().endsWith("Builder")) {
                try {
                    builder = (ConfigInstance.Builder) c.getConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Could not instantiate builder for " + clazz.getName(), e);
                }
            }
        }
        if (builder == null) {
            throw new RuntimeException("Could not find builder for " + clazz.getName());
        } else {
            return builder;
        }
    }

}
