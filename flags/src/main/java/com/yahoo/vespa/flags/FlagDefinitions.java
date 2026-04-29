// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * Service Provider Interface that allows additional flag definitions, defined outside the
 * {@code flags} bundle, to be registered with {@link Flags} and {@link PermanentFlags}.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} when {@link Flags}
 * is class-initialized, and {@link #register()} is invoked at most once per {@link Flags}
 * class initialization (i.e. once per classloader that loads this class). Implementations
 * are expected to register their flags by calling {@code Flags.defineXxxFlag(...)} (typically
 * by forcing class-initialization of the classes that hold the flag fields).</p>
 *
 * <p>To register an implementation, declare it as a service in
 * {@code META-INF/services/com.yahoo.vespa.flags.FlagDefinitions}.</p>
 *
 * @author hakonhall
 */
public interface FlagDefinitions {

    /**
     * Register additional flag definitions.
     *
     * <p>Called from the static initializer of {@link Flags}, after the static flag map
     * has been initialized — at most once per {@link Flags} class initialization.
     * Implementations should call {@code Flags.defineXxxFlag(...)} (directly or
     * transitively) for every flag they own.</p>
     */
    void register();
}
