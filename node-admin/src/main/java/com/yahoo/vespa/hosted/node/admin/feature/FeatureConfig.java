// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.feature;

import com.yahoo.vespa.configsource.exports.ConfigSupplier;
import io.netty.util.internal.ConcurrentSet;

import javax.annotation.concurrent.ThreadSafe;

/**
 * A {@code FeatureConfig} is a base class for classes that wants to expose some or
 * all parts of a single feature config ({@link FeatureConfigSource}).
 *
 * <p>For instance, {@code FeatureConfig} can be used to guard new features using {@link FeatureFlag}.
 *
 * <p>A concrete subclass use {@link #getFeatureConfigSnapshot()} to get the current value of
 * the feature config identified by a FeatureConfigId.
 *
 * <p>Compared to cloud config, feature config have the following benefits:
 *
 * <ul>
 *     <li>feature values can change at any time and independent of each other
 *     <li>feature value changes doesn't require component graph rebuilding
 *     <li>feature value modifications doesn't require a config server
 *     <li>what parts of the config API that is exposed to to the user is entirely decided
 *         by the concrete subclass
 *     <li>a particular feature can be highly reusable, e.g. a FeatureFlag can be used wherever
 *         a boolean is needed, and each FeatureFlag is independent
 *     <li>supports simple Jackson deserialization
 * </ul>
 *
 * <p>However, features may slip through to production without proper testing,
 * and (like cloud config) changes are unorchestrated. Therefore, use Features only
 * when there is no good alternative. Some examples of good use cases for features are:
 *
 * <ul>
 *     <li>rolling out new functionality that is too risky to roll out normally
 *     <li>allow quick roll back of new functionality if it needs to be fixed before next release
 *     <li>need to change parameters faster or more frequently than the release schedule
 *     <li>testing out experimental code
 * </ul>
 *
 * <p>Features should be removed once they are not strictly needed anymore.
 *
 * @author hakon
 */
@ThreadSafe
public abstract class FeatureConfig<T> {
    private static final ConcurrentSet<FeatureConfigId> features = new ConcurrentSet<>();

    private final FeatureConfigId configId;
    private final T defaultValue;
    private final ConfigSupplier<T> configSupplier;

    /**
     * Variant of {@link #FeatureConfig(FeatureConfigSource, FeatureConfigId, Class, Object)}
     * which uses the default constructor of the Jackson class as the default value
     */
    protected FeatureConfig(FeatureConfigSource backend, FeatureConfigId configId, Class<T> jacksonClass) {
        this(backend, configId, jacksonClass, invokeDefaultConstructor(jacksonClass));
    }

    /**
     * @param configSource The backend managing the feature configs.
     * @param configId     The ID of the feature config to wrap.
     * @param jacksonClass The Jackson class to use for deserialization and to return.
     * @param defaultValue The default value of the feature config, if the config source
     *                     doesn't specify any value.
     */
    protected FeatureConfig(FeatureConfigSource configSource,
                            FeatureConfigId configId,
                            Class<T> jacksonClass,
                            T defaultValue) {
        // We can add support for multiple accessors later, in case all clients should
        // probably provide identical accessor token ~ CanonicalClass.class.getClass().toString()
        // as a safety measure against name clashes. Some concrete FeatureConfig may not even support
        // multiple instances, e.g. have in-memory adjustment of values.
        if (!features.add(configId)) {
            throw new IllegalArgumentException(FeatureConfig.class.getSimpleName() +
                    " '" + configId + "' is already registered");
        }

        this.configId = configId;
        this.defaultValue = defaultValue;
        this.configSupplier = configSource.newJacksonSupplier(configId, jacksonClass);
    }

    /** The ID of the config this {@code FeatureConfig} is backed by. */
    protected final FeatureConfigId getFeatureConfigId() {
        return configId;
    }

    /**  Get the current value of the feature. */
    protected final T getFeatureConfigSnapshot() {
        return configSupplier.getSnapshot().orElse(defaultValue);
    }

    private static <T> T invokeDefaultConstructor(Class<T> clazz) {
        try {
            return clazz.newInstance();
        } catch (IllegalAccessException | InstantiationException e) {
            throw new IllegalArgumentException(
                    "Failed to invoke the default constructor of " + clazz.getName(), e);
        }
    }
}
