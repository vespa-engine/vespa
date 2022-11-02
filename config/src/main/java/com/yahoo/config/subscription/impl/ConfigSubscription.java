// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigSet;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.config.subscription.DirSource;
import com.yahoo.config.subscription.FileSource;
import com.yahoo.config.subscription.JarSource;
import com.yahoo.config.subscription.RawSource;
import com.yahoo.text.internal.SnippetGenerator;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.PayloadChecksums;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.DefContent;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;

/**
 * Represents one active subscription to one config
 *
 * @author Vegard Havdal
 */
public abstract class ConfigSubscription<T extends ConfigInstance> {

    protected static final Logger log = Logger.getLogger(ConfigSubscription.class.getName());
    private final AtomicReference<ConfigState<T>> config = new AtomicReference<>();
    protected final ConfigKey<T> key;
    protected final Class<T> configClass;
    private volatile RuntimeException exception = null;
    private State state = State.OPEN;

    public static class ConfigState<T extends ConfigInstance> {

        private final boolean configChanged;
        private final boolean generationChanged;
        private final T config;
        private final Long generation;
        private final boolean applyOnRestart;
        private final PayloadChecksums payloadChecksums;

        private ConfigState(boolean generationChanged,
                            Long generation,
                            boolean applyOnRestart,
                            boolean configChanged,
                            T config,
                            PayloadChecksums payloadChecksums) {
            this.generationChanged = generationChanged;
            this.generation = generation;
            this.applyOnRestart = applyOnRestart;
            this.configChanged = configChanged;
            this.config = config;
            this.payloadChecksums = payloadChecksums;
        }

        private ConfigState(Long generation, T config, PayloadChecksums payloadChecksums) {
            this(false, generation, false, false, config, payloadChecksums);
        }

        private ConfigState() {
            this(false, 0L, false, false, null, PayloadChecksums.empty());
        }

        private ConfigState<T> createUnchanged() {  return new ConfigState<>(generation, config, payloadChecksums); }

        public boolean isConfigChanged() { return configChanged; }

        public boolean isGenerationChanged() { return generationChanged; }

        public Long getGeneration() { return generation; }

        public boolean applyOnRestart() { return applyOnRestart; }

        public T getConfig() { return config; }

        public PayloadChecksums getChecksums() { return payloadChecksums; }

    }

    /**
     * If non-null: The user has set this generation explicitly. nextConfig should take this into account.
     * Access to these variables _must_ be synchronized, as nextConfig and reload() is likely to be run from
     * independent threads.
     */
    private final AtomicReference<Long> reloadedGeneration = new AtomicReference<>();

    enum State {
        OPEN, CLOSED
    }

    /**
     * Initializes one subscription
     *
     * @param key        a {@link ConfigKey}
     */
    ConfigSubscription(ConfigKey<T> key) {
        this.key = key;
        this.configClass = key.getConfigClass();
        this.config.set(new ConfigState<>());
        getConfigState().getChecksums().removeChecksumsOfType(MD5);  // TODO: Temporary until we don't use md5 anymore
    }

    /**
     * Correct type of ConfigSubscription instance based on type of source or form of config id
     *
     * @param key        a {@link ConfigKey}
     * @return a subclass of a ConfigsSubscription
     */
    public static <T extends ConfigInstance> ConfigSubscription<T> get(ConfigKey<T> key, JrtConfigRequesters requesters,
                                                                       ConfigSource source, TimingValues timingValues) {
        String configId = key.getConfigId();
        if (source instanceof RawSource || configId.startsWith("raw:")) return getRawSub(key, source);
        if (source instanceof FileSource || configId.startsWith("file:")) return getFileSub(key, source);
        if (source instanceof DirSource || configId.startsWith("dir:")) return getDirFileSub(key, source);
        if (source instanceof JarSource || configId.startsWith("jar:")) return getJarSub(key, source);
        if (source instanceof ConfigSet cset) return new ConfigSetSubscription<>(key, cset);
        if (source instanceof ConfigSourceSet csset) {
            return new JRTConfigSubscription<>(key, requesters.getRequester(csset, timingValues), timingValues);
        }
        throw new IllegalArgumentException("Unknown source type: " + source);
    }

    private static <T extends ConfigInstance> JarConfigSubscription<T> getJarSub(ConfigKey<T> key, ConfigSource source) {
        String jarName;
        String path = "config/";
        if (source instanceof JarSource js) {
            jarName = js.getJarFile().getName();
            if (js.getPath() != null) path = js.getPath();
        } else {
            jarName = key.getConfigId().replace("jar:", "").replaceFirst("!/.*", "");
            if (key.getConfigId().contains("!/")) path = key.getConfigId().replaceFirst(".*!/", "");
        }
        return new JarConfigSubscription<>(key, jarName, path);
    }

    private static <T extends ConfigInstance> ConfigSubscription<T> getFileSub(ConfigKey<T> key, ConfigSource source) {
        FileSource file = source instanceof FileSource fileSource ? fileSource
                                                                  : new FileSource(new File(key.getConfigId().replace("file:", "")));
        return new FileConfigSubscription<>(key, file);
    }

    private static <T extends ConfigInstance> ConfigSubscription<T> getRawSub(ConfigKey<T> key, ConfigSource source) {
        String payload = ((source instanceof RawSource)
                ? ((RawSource) source).payload
                : key.getConfigId().replace("raw:", ""));
        return new RawConfigSubscription<>(key, payload);
    }

    private static <T extends ConfigInstance> ConfigSubscription<T> getDirFileSub(ConfigKey<T> key, ConfigSource source) {
        DirSource dir = source instanceof DirSource dirSource ? dirSource
                                                              : new DirSource(new File(key.getConfigId().replace("dir:", "")));
        return new FileConfigSubscription<>(key, dir.getFile(getConfigFilename(key)));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ConfigSubscription<?> cs && key.equals(cs.key);
    }

    /**
     * Called from {@link ConfigSubscriber} when the changed status of this config is propagated to the clients
     */
    public boolean isConfigChangedAndReset(Long requiredGen) {
        ConfigState<T> prev = config.get();
        while (prev.getGeneration().equals(requiredGen) && !config.compareAndSet(prev, prev.createUnchanged())) {
            prev = config.get();
        }
        // A false positive is a lot better than a false negative
        return !prev.getGeneration().equals(requiredGen) || prev.isConfigChanged();
    }

    void setConfig(Long generation, boolean applyOnRestart, T config, PayloadChecksums payloadChecksums) {
        this.config.set(new ConfigState<>(true, generation, applyOnRestart, true, config, payloadChecksums));
    }

    void setConfigAndGeneration(Long generation, boolean applyOnRestart, T config, PayloadChecksums payloadChecksums) {
        T previousConfig = this.config.get().getConfig();
        boolean configChanged = ! Objects.equals(previousConfig, config);
        if (previousConfig != null && configChanged) {
            SnippetGenerator generator = new SnippetGenerator();
            int sizeHint = 500;
            log.log(Level.WARNING, "Config has changed unexpectedly for " + key + ", generation " + generation +
                    ", config in state :" + generator.makeSnippet(previousConfig.toString(), sizeHint) + ", new config: " +
                    generator.makeSnippet(config.toString(), sizeHint) +
                    ". This likely happened because config changed on a previous generation" +
                    ", look for earlier entry in log with warning about config changing without a change in config generation.");
        }
        this.config.set(new ConfigState<>(true, generation, applyOnRestart, configChanged, config, payloadChecksums));
    }

    /**
     * Used by {@link FileConfigSubscription} and {@link ConfigSetSubscription}
     */
    protected void setConfigIncGen(T config) {
        ConfigState<T> prev = this.config.get();
        this.config.set(new ConfigState<>(true, prev.getGeneration() + 1, prev.applyOnRestart(), true, config, prev.payloadChecksums));
    }

    protected void setConfigIfChanged(T config) {
        ConfigState<T> prev = this.config.get();
        this.config.set(new ConfigState<>(true, prev.getGeneration(), prev.applyOnRestart(), !Objects.equals(prev.getConfig(), config), config, prev.payloadChecksums));
    }

    void setGeneration(Long generation) {
        ConfigState<T> prev = config.get();
        this.config.set(new ConfigState<>(true, generation, prev.applyOnRestart(), prev.isConfigChanged(), prev.getConfig(), prev.payloadChecksums));
    }

    void setApplyOnRestart(boolean applyOnRestart) {
        ConfigState<T> prev = config.get();
        this.config.set(new ConfigState<>(prev.isGenerationChanged(), prev.getGeneration(), applyOnRestart, prev.isConfigChanged(), prev.getConfig(), prev.payloadChecksums));
    }

    /**
     * The config state object of this subscription
     *
     * @return the ConfigInstance (the config) of this subscription
     */
    public ConfigState<T> getConfigState() {
        return config.get();
    }

    /**
     * The class of the subscription's desired {@link ConfigInstance}
     *
     * @return the config class
     */
    public Class<T> getConfigClass() {
        return configClass;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder(key.toString());
        ConfigState<T> c = config.get();
        s.append(", Current generation: ").append(c.getGeneration())
                .append(", Generation changed: ").append(c.isGenerationChanged())
                .append(", Config changed: ").append(c.isConfigChanged());
        if (exception != null)
            s.append(", Exception: ").append(exception);
        return s.toString();
    }

    /**
     * The config key which this subscription uses to identify its config
     *
     * @return the ConfigKey for this subscription
     */
    public ConfigKey<T> getKey() {
        return key;
    }

    /**
     * Polls this subscription for a change. The method is guaranteed to use all of the given timeout before returning false. It will also take into account a user-set generation,
     * that can be set by {@link ConfigSubscriber#reload(long)}.
     *
     * @param timeout in milliseconds
     * @return false if timed out, true if generation or config or {@link #exception} changed. If true, the {@link #config} field will be set also.
     * has changed
     */
    public abstract boolean nextConfig(long timeout);

    /**
     * Will block until the next {@link #nextConfig(long)} is guaranteed to return an answer (or throw) immediately (i.e. not block)
     *
     * @param timeout in milliseconds
     * @return false if timed out
     */
    public abstract boolean subscribe(long timeout);

    /**
     * Called by for example network threads to signal that the user thread should throw this exception immediately
     *
     * @param e a RuntimeException
     */
    public void setException(RuntimeException e) {
        this.exception = e;
    }

    /**
     * Gets an exception set by for example a network thread. If not null, it indicates that it should be
     * thrown in the user's thread immediately.
     *
     * @return a RuntimeException if there exists one
     */
    public RuntimeException getException() {
        return exception;
    }

    /**
     * Returns true if an exception set by for example a network thread has been caught.
     *
     * @return true if there exists an exception for this subscription
     */
    boolean hasException() {
        return exception != null;
    }

    public void close() {
        state = State.CLOSED;
    }

    public boolean isClosed() { return state == State.CLOSED; }

    /**
     * Returns the file name corresponding to the given key's defName.
     *
     * @param key a {@link ConfigKey}
     * @return file name
     */
    static <T extends ConfigInstance> String getConfigFilename(ConfigKey<T> key) {
        return key.getName() + ".cfg";
    }

    /**
     * Force this into the given generation, used in testing
     *
     * @param generation a config generation
     */
    public void reload(long generation) {
        reloadedGeneration.set(generation);
    }

    /**
     * True if someone has set the {@link #reloadedGeneration} number by calling {@link #reload(long)}
     * and hence wants to force a given generation programmatically. If that is the case,
     * sets the generation and flags it as changed accordingly.
     *
     * @return true if {@link #reload(long)} has been called, false otherwise
     */
    protected boolean checkReloaded() {
        Long reloaded = reloadedGeneration.getAndSet(null);
        if (reloaded != null) {
            setGeneration(reloaded);
            return true;
        }
        return false;
    }

    /**
     * The config definition schema
     *
     * @return the config definition for this subscription
     */
    public DefContent getDefContent() {
        return (DefContent.fromClass(configClass));
    }
}
