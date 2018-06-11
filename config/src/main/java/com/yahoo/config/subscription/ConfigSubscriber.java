// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.impl.ConfigSubscription;
import com.yahoo.config.subscription.impl.JRTConfigRequester;
import com.yahoo.log.LogLevel;
import com.yahoo.yolean.Exceptions;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.TimingValues;

/**
 * Used for subscribing to one or more configs. Can optionally be given a {@link ConfigSource} for the configs
 * that will be used when {@link #subscribe(Class, String)} is called.
 *
 * {@link #subscribe(Class, String)} on the configs needed, call {@link #nextConfig(long)} and get the config from the
 * {@link ConfigHandle} which {@link #subscribe(Class, String)} returned.
 *
 * @author vegardh
 */
public class ConfigSubscriber {

    private Logger log = Logger.getLogger(getClass().getName());
    private State state = State.OPEN;
    protected List<ConfigHandle<? extends ConfigInstance>> subscriptionHandles = new ArrayList<>();
    private final ConfigSource source;

    /** The last complete config generation received by this */
    private long generation = -1;

    /** Whether the last generation received was due to a system-internal redeploy, not an application package change */
    private boolean internalRedeploy = false;

    /**
     * Reuse requesters for equal source sets, limit number if many subscriptions.
     */
    protected Map<ConfigSourceSet, JRTConfigRequester> requesters = new HashMap<>();

    /**
     * The states of the subscriber. Affects the validity of calling certain methods.
     *
     */
    protected enum State {
        OPEN, FROZEN, CLOSED
    }

    /**
     * Constructs a new subscriber. The default Vespa network config source will be used, which is the address of
     * a config proxy (part of vespa_base) running locally. It can also be changed by setting VESPA_CONFIG_SOURCES.
     */
    public ConfigSubscriber() {
        this(JRTConfigRequester.defaultSourceSet);
    }

    /**
     * Constructs a new subscriber with the given source.
     *
     * @param source a {@link ConfigSource} that will be used when {@link #subscribe(Class, String)} is called.
     */
    public ConfigSubscriber(ConfigSource source) {
        this.source = source;
    }

    /**
     * Subscribes on the given type of {@link ConfigInstance} with the given config id.
     *
     * The method blocks until the first config is ready to be fetched with {@link #nextConfig()}.
     *
     * @param configClass The class, typically generated from a def-file using config-class-plugin
     * @param configId Identifies the service in vespa-services.xml, or null if you are using a local {@link ConfigSource} which does not use config id.
     * Also supported: raw:, file:, dir: or jar: config id which addresses config locally in the same way.
     *
     * @return a ConfigHandle
     */
    public <T extends ConfigInstance> ConfigHandle<T> subscribe(Class<T> configClass, String configId) {
        return subscribe(configClass, configId, source, new TimingValues());
    }

    /**
     * Subscribes on the given type of {@link ConfigInstance} with the given config id and subscribe timeout.
     *
     * The method blocks until the first config is ready to be fetched with {@link #nextConfig()}.
     *
     * @param configClass The class, typically generated from a def-file using config-class-plugin
     * @param configId    Identifies the service in vespa-services.xml, or possibly raw:, file:, dir: or jar: type config which addresses config locally.
     * @param timeoutMillis The time to wait for a config to become available, in milliseconds
     * @return a ConfigHandle
     */
    public <T extends ConfigInstance> ConfigHandle<T> subscribe(Class<T> configClass, String configId, long timeoutMillis) {
        return subscribe(configClass, configId, source, new TimingValues().setSubscribeTimeout(timeoutMillis));
    }

    // for testing
    <T extends ConfigInstance> ConfigHandle<T> subscribe(Class<T> configClass, String configId, ConfigSource source, TimingValues timingValues) {
        checkStateBeforeSubscribe();
        final ConfigKey<T> configKey = new ConfigKey<>(configClass, configId);
        ConfigSubscription<T> sub = ConfigSubscription.get(configKey, this, source, timingValues);
        ConfigHandle<T> handle = new ConfigHandle<>(sub);
        subscribeAndHandleErrors(sub, configKey, handle, timingValues);
        return handle;
    }

    protected void checkStateBeforeSubscribe() {
        if (state != State.OPEN)
            throw new IllegalStateException("Adding subscription after calling nextConfig() is not allowed");
    }

    protected void subscribeAndHandleErrors(ConfigSubscription<?> sub, ConfigKey<?> configKey, ConfigHandle<?> handle, TimingValues timingValues) {
        subscriptionHandles.add(handle);
        // Must block here until something available from the subscription, so we know that it offers something when the user calls nextConfig
        boolean subOk = sub.subscribe(timingValues.getSubscribeTimeout());
        throwIfExceptionSet(sub);
        if (!subOk) {
            //sub.close();
            //subscriptionHandles.remove(handle);
            throw new ConfigurationRuntimeException("Subscribe for '" + configKey + "' timed out (timeout was " + timingValues.getSubscribeTimeout() + " ms): " + sub);
        }
    }

    /**
     * Use this for waiting for a new config that has changed.
     *
     * Returns true if:
     *
     * It is the first time nextConfig() is called on this subscriber, and the framework has fetched config for all subscriptions. (Typically a first time config.)
     *
     * or
     *
     * All configs for the subscriber have a new generation since the last time nextConfig() was called, AND they have the same generation AND there is a change in config for at least one
     * of the configs. (Typically calls for a reconfig.)
     *
     * You can check which configs are changed by calling {@link ConfigHandle#isChanged()} on the handle you got from {@link #subscribe(Class, String)}.
     *
     * If the call times out (timeout 1000 ms), no handle will have the changed flag set. You should not configure anything then.
     *
     * @return true if a config/reconfig of your system should happen
     * @throws ConfigInterruptedException if thread performing this call interrupted.
     */
    public boolean nextConfig() {
        return nextConfig(TimingValues.defaultNextConfigTimeout);
    }

    /**
     * Use this for waiting for a new config that has changed, with the given timeout.
     *
     * Returns true if:
     *
     * It is the first time nextConfig() is called on this subscriber, and the framework has fetched config for all subscriptions. (Typically a first time config.)
     *
     * or
     *
     * All configs for the subscriber have a new generation since the last time nextConfig() was called, AND they have the same generation AND there is a change in config for at least one
     * of the configs. (Typically calls for a reconfig.)
     *
     * You can check which configs are changed by calling {@link ConfigHandle#isChanged()} on the handle you got from {@link #subscribe(Class, String)}.
     *
     * If the call times out, no handle will have the changed flag set. You should not configure anything then.
     *
     * @param timeoutMillis timeout in milliseconds
     * @return true if a config/reconfig of your system should happen
     * @throws ConfigInterruptedException if thread performing this call interrupted.
     */
    public boolean nextConfig(long timeoutMillis) {
        return acquireSnapshot(timeoutMillis, true);
    }

    /**
     * Use this for waiting for a new config generation.
     *
     * Returns true if:
     *
     * It is the first time nextGeneration() is called on this subscriber, and the framework has fetched config for all subscriptions. (Typically a first time config.)
     *
     * or
     *
     * All configs for the subscriber have a new generation since the last time nextGeneration() was called, AND they have the same generation. Note that
     * none of the configs have to be changed, but they might be.
     *
     *
     * You can check which configs are changed by calling {@link ConfigHandle#isChanged()} on the handle you got from {@link #subscribe(Class, String)}.
     *
     * If the call times out (timeout 1000 ms), no handle will have the changed flag set. You should not configure anything then.
     *
     * @return true if generations for all configs have been updated.
     * @throws ConfigInterruptedException if thread performing this call interrupted.
     */
    public boolean nextGeneration() {
        return nextGeneration(TimingValues.defaultNextConfigTimeout);
    }

    /**
     * Use this for waiting for a new config generation, with the given timeout
     *
     * Returns true if:
     *
     * It is the first time nextGeneration() is called on this subscriber, and the framework has fetched config for all subscriptions. (Typically a first time config.)
     *
     * or
     *
     * All configs for the subscriber have a new generation since the last time nextGeneration() was called, AND they have the same generation. Note that
     * none of the configs have to be changed, but they might be.
     *
     * You can check which configs are changed by calling {@link ConfigHandle#isChanged()} on the handle you got from {@link #subscribe(Class, String)}.
     *
     * If the call times out (timeout 1000 ms), no handle will have the changed flag set. You should not configure anything then.
     *
     * @param timeoutMillis timeout in milliseconds
     * @return true if generations for all configs have been updated.
     * @throws ConfigInterruptedException if thread performing this call interrupted.
     */
    public boolean nextGeneration(long timeoutMillis) {
        return acquireSnapshot(timeoutMillis, false);
    }

    /**
     * Acquire a snapshot of all configs with the same generation within a timeout.
     *
     * @param timeoutInMillis timeout to wait in milliseconds
     * @param requireChange if set, at least one config have to change
     * @return true, if a new config generation has been found for all configs (additionally requires
     *         that at lest one of them has changed if <code>requireChange</code> is true), false otherwise
     */
    private boolean acquireSnapshot(long timeoutInMillis, boolean requireChange) {
        if (state == State.CLOSED) return false;
        long started = System.currentTimeMillis();
        long timeLeftMillis = timeoutInMillis;
        state = State.FROZEN;
        boolean anyConfigChanged = false;
        boolean allGenerationsChanged = true;
        boolean allGenerationsTheSame = true;
        Long currentGen = null;
        for (ConfigHandle<? extends ConfigInstance> h : subscriptionHandles) {
            h.setChanged(false); // Reset this flag, if it was set, the user should have acted on it the last time this method returned true.
        }
        boolean reconfigDue;
        boolean internalRedeployOnly = true;
        do {
            // Keep on polling the subscriptions until we have a new generation across the board, or it times out
            for (ConfigHandle<? extends ConfigInstance> h : subscriptionHandles) {
                ConfigSubscription<? extends ConfigInstance> subscription = h.subscription();
                if ( ! subscription.nextConfig(timeLeftMillis)) {
                    // This subscriber has no new state and we know it has exhausted all time
                    return false;
                }
                throwIfExceptionSet(subscription);
                ConfigSubscription.ConfigState<? extends ConfigInstance> config = subscription.getConfigState();
                if (currentGen == null) currentGen = config.getGeneration();
                if ( ! currentGen.equals(config.getGeneration())) allGenerationsTheSame = false;
                allGenerationsChanged = allGenerationsChanged && config.isGenerationChanged();
                if (config.isConfigChanged()) anyConfigChanged = true;
                internalRedeployOnly = internalRedeployOnly && config.isInternalRedeploy();
                timeLeftMillis = timeLeftMillis - (System.currentTimeMillis() - started);
            }
            reconfigDue = (anyConfigChanged || !requireChange) && allGenerationsChanged && allGenerationsTheSame;
            if (!reconfigDue && timeLeftMillis > 0) {
                sleep();
            }
        } while (!reconfigDue && timeLeftMillis > 0);
        if (reconfigDue) {
            // This indicates the clients will possibly reconfigure their services, so "reset" changed-logic in subscriptions.
            // Also if appropriate update the changed flag on the handler, which clients use.
            markSubsChangedSeen(currentGen);
            Logger.getLogger("REDEPLOY").info("ConfigSubscriber.acquireSnapshot: Received config generation " + generation + " with internalRedeploy=" + internalRedeployOnly);
            internalRedeploy = internalRedeployOnly;
            generation = currentGen;
        }
        return reconfigDue;
    }

    private void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            throw new ConfigInterruptedException(e);
        }
    }

    /**
     * If a {@link ConfigSubscription} has its exception set, reset that field and throw it
     *
     * @param sub {@link ConfigSubscription}
     */
    protected void throwIfExceptionSet(ConfigSubscription<? extends ConfigInstance> sub) {
        RuntimeException subThrowable = sub.getException();
        if (subThrowable != null) {
            sub.setException(null);
            throw subThrowable;
        }
    }

    private void markSubsChangedSeen(Long requiredGen) {
        for (ConfigHandle<? extends ConfigInstance> h : subscriptionHandles) {
            ConfigSubscription<? extends ConfigInstance> sub = h.subscription();
            h.setChanged(sub.isConfigChangedAndReset(requiredGen));
        }
    }

    /**
     * Closes all open {@link ConfigSubscription}s
     */
    public void close() {
        state = State.CLOSED;
        for (ConfigHandle<? extends ConfigInstance> h : subscriptionHandles) {
            h.subscription().close();
        }
        closeRequesters();
        log.log(LogLevel.DEBUG, "Config subscriber has been closed.");
    }

    /**
     * Closes all open requesters
     */
    protected void closeRequesters() {
        for (JRTConfigRequester requester : requesters.values()) {
            requester.close();
        }
    }

    @Override
    public String toString() {
        String ret = "Subscriber state:" + state;
        for (ConfigHandle<?> h : subscriptionHandles) {
            ret = ret + "\n" + h.toString();
        }
        return ret;
    }

    /**
     * Convenience method to start a daemon thread called "Vespa config thread" with the given runnable. If you want the runnable to
     * handle a {@link ConfigSubscriber} or {@link ConfigHandle} you have declared locally outside, declare them as final to make it work.
     *
     * @param runnable a class implementing {@link java.lang.Runnable}
     * @return the newly started thread
     */
    public Thread startConfigThread(Runnable runnable) {
        Thread t = new Thread(runnable);
        t.setDaemon(true);
        t.setName("Vespa config thread");
        t.start();
        return t;
    }

    protected State state() {
        return state;
    }

    /**
     * Sets all subscriptions under this subscriber to have the given generation. This is intended for testing, to emulate a
     * reload-config operation.
     *
     * @param generation a generation number
     */
    public void reload(long generation) {
        for (ConfigHandle<?> h : subscriptionHandles) {
            h.subscription().reload(generation);
        }
    }

    /**
     * The source used by this subscriber.
     *
     * @return the {@link ConfigSource} used by this subscriber
     */
    public ConfigSource getSource() {
        return source;
    }

    /**
     * Implementation detail, do not use.
     * @return requesters
     */
    public Map<ConfigSourceSet, JRTConfigRequester> requesters() {
        return requesters;
    }

    public boolean isClosed() {
        return state == State.CLOSED;
    }

    /**
     * Use this convenience method if you only want to subscribe on <em>one</em> config, and want generic error handling.
     * Implement {@link SingleSubscriber} and pass to this method.
     * You will get initial config, and a config thread will be started. The method will throw in your thread if initial
     * configuration fails, and the config thread will print a generic error message (but continue) if it fails thereafter. The config
     * thread will stop if you {@link #close()} this {@link ConfigSubscriber}.
     *
     * @param <T> ConfigInstance type
     * @param singleSubscriber The object to receive config
     * @param configClass      The class, typically generated from a def-file using config-class-plugin
     * @param configId         Identifies the service in vespa-services.xml
     * @return The handle of the config
     * @see #startConfigThread(Runnable)
     */
    public <T extends ConfigInstance> ConfigHandle<T> subscribe(final SingleSubscriber<T> singleSubscriber, Class<T> configClass, String configId) {
        if (!subscriptionHandles.isEmpty())
            throw new IllegalStateException("Can not start single-subscription because subscriptions were previously opened on this.");
        final ConfigHandle<T> handle = subscribe(configClass, configId);
        if (!nextConfig())
            throw new ConfigurationRuntimeException("Initial config of " + configClass.getName() + " failed.");
        singleSubscriber.configure(handle.getConfig());
        startConfigThread(new Runnable() {
            @Override
            public void run() {
                while (!isClosed()) {
                    try {
                        if (nextConfig()) {
                            if (handle.isChanged()) singleSubscriber.configure(handle.getConfig());
                        }
                    } catch (Exception e) {
                        log.log(LogLevel.ERROR, "Exception from config system, continuing config thread: " + Exceptions.toMessageString(e));
                    }
                }
            }
        });
        return handle;
    }

    /**
     * The current generation of configs known by this subscriber.
     *
     * @return the current generation of configs known by this subscriber
     */
    public long getGeneration() {
        return generation;
    }

    /**
     * Whether the current config generation received by this was due to a system-internal redeploy,
     * not an application package change
     */
    public boolean isInternalRedeploy() { return internalRedeploy; }

    /**
     * Convenience interface for clients who only subscribe to one config. Implement this, and pass it to {@link ConfigSubscriber#subscribe(SingleSubscriber, Class, String)}.
     *
     * @author vegardh
     */
    public interface SingleSubscriber<T extends ConfigInstance> {
        void configure(T config);
    }

    /**
     * Finalizer to ensure that we do not leak resources on reconfig. Though finalizers are bad,
     * this is not a performance critical object as it will be deconstructed typically container reconfig.
     */
    @Override
    @SuppressWarnings("deprecation")  // finalize() is deprecated from Java 9
    protected void finalize() throws Throwable {
        try {
            if (!isClosed()) {
                close();
            }
        } finally {
            super.finalize();
        }
    }


}
