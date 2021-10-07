// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequest;
import com.yahoo.vespa.config.protocol.Payload;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static com.yahoo.vespa.config.PayloadChecksum.Type.MD5;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;

/**
 * A config subscription for a config instance, gets config using Vespa RPC from a config source
 * (config proxy or config server).
 *
 * @author vegardh
 */
public class JRTConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    private JRTConfigRequester requester;
    private final TimingValues timingValues;

    // Last time we got an OK JRT callback
    private Instant lastOK = Instant.MIN;

    /**
     * A queue containing either zero or one (the newest) request that got a callback from JRT,
     * but has not yet been handled.
     */
    private BlockingQueue<JRTClientConfigRequest> reqQueue = new LinkedBlockingQueue<>();
    private ConfigSourceSet sources;

    public JRTConfigSubscription(ConfigKey<T> key, ConfigSubscriber subscriber, ConfigSource source, TimingValues timingValues) {
        super(key, subscriber);
        this.timingValues = timingValues;
        if (source instanceof ConfigSourceSet) {
            this.sources = (ConfigSourceSet) source;
        }
    }

    @Override
    public boolean nextConfig(long timeoutMillis) {
        // Note: since the JRT callback thread will clear the queue first when it inserts a brand new element,
        // (see #updateConfig()) there is a race here. However: the caller will handle it no matter what it gets
        // from the queue here, the important part is that local state on the subscription objects is preserved.

        // Poll the queue for a next config until timeout
        JRTClientConfigRequest jrtReq = pollQueue(timeoutMillis);
        if (jrtReq == null) return newConfigOrException();

        log.log(FINE, () -> "Polled queue and found config " + jrtReq);
        // Might set the following (caller must check):
        // generation, generation changed, config, config changed
        // Important: it never <em>resets</em> those flags, we must persist that state until the
        // ConfigSubscriber clears it
        if (jrtReq.hasUpdatedGeneration()) {
            setApplyOnRestart(jrtReq.responseIsApplyOnRestart());
            if (jrtReq.hasUpdatedConfig()) {
                setNewConfig(jrtReq);
            } else {
                setGeneration(jrtReq.getNewGeneration());
            }
        }

        return newConfigOrException();
    }

    private boolean newConfigOrException() {
        // These flags may have been left true from a previous call, since ConfigSubscriber's nextConfig
        // not necessarily returned true and reset the flags then
        ConfigState<T> configState = getConfigState();
        return configState.isGenerationChanged() || configState.isConfigChanged() || hasException();
    }

    /**
     * Polls the callback queue for new config
     *
     * @param timeoutMillis timeout when polling (returns after at most this time)
     */
    private JRTClientConfigRequest pollQueue(long timeoutMillis) {
        try {
            // Only valid responses are on queue, no need to validate
            return reqQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e1) {
            throw new ConfigInterruptedException(e1);
        }
    }

    protected void setNewConfig(JRTClientConfigRequest jrtReq) {
        Exception badConfigE = null;
        T configInstance = null;
        try {
            configInstance = toConfigInstance(jrtReq);
        } catch (IllegalArgumentException e) {
            badConfigE = e;
        }
        setConfig(jrtReq.getNewGeneration(), jrtReq.responseIsApplyOnRestart(), configInstance, jrtReq.getNewChecksums());
        if (badConfigE != null) {
            throw new IllegalArgumentException("Bad config from jrt", badConfigE);
        }
    }

    /**
     * This method should ideally throw new MissingConfig/Configuration exceptions and let the caller
     * catch them. However, this would make the code in JRT/File/RawSource uglier.
     * Alternatively, it could return a SetConfigStatus object with an int and an error message.
     *
     * @param jrtRequest a config request
     * @return an instance of a config class (subclass of ConfigInstance)
     */
    private T toConfigInstance(JRTClientConfigRequest jrtRequest) {
        Payload payload = jrtRequest.getNewPayload();
        ConfigPayload configPayload = ConfigPayload.fromUtf8Array(payload.withCompression(CompressionType.UNCOMPRESSED).getData());
        T configInstance = configPayload.toInstance(configClass, jrtRequest.getConfigKey().getConfigId());
        configInstance.setConfigMd5(jrtRequest.getNewChecksums().getForType(MD5).asString()); // Note: Sets configmd5 in ConfigInstance
        return configInstance;
    }

    // Will be called by JRTConfigRequester when there is a config with new generation for this subscription
    void updateConfig(JRTClientConfigRequest jrtReq) {
        // We only want this latest generation to be in the queue, we do not preserve history in this system
        reqQueue.clear();
        if ( ! reqQueue.offer(jrtReq))
            setException(new ConfigurationRuntimeException("Failed offering returned request to queue of subscription " + this));
    }

    @Override
    public boolean subscribe(long timeout) {
        lastOK = Instant.now();
        requester = getRequester();
        requester.request(this);
        JRTClientConfigRequest req = reqQueue.peek();
        while (req == null && (Instant.now().isBefore(lastOK.plus(Duration.ofMillis(timeout))))) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new ConfigInterruptedException(e);
            }
            req = reqQueue.peek();
        }
        return req != null;
    }

    private JRTConfigRequester getRequester() {
        JRTConfigRequester requester = subscriber.requesters().get(sources);
        if (requester == null) {
            requester = JRTConfigRequester.create(sources, timingValues);
            subscriber.requesters().put(sources, requester);
        }
        return requester;
    }

    @Override
    @SuppressWarnings("serial")
    public void close() {
        super.close();
        reqQueue = new LinkedBlockingQueue<>() {
            @SuppressWarnings("NullableProblems")
            @Override
            public void put(JRTClientConfigRequest e) {
                // When closed, throw away all requests that callbacks try to put
            }
        };
    }

    /**
     * The timing values of this
     *
     * @return timing values
     */
    public TimingValues timingValues() {
        return timingValues;
    }

    // Used in integration tests
    @SuppressWarnings("UnusedDeclaration")
    public JRTConfigRequester requester() {
        return requester;
    }

    @Override
    public void reload(long generation) {
        log.log(FINE, "reload() is without effect on a JRTConfigSubscription.");
    }

    void setLastCallBackOKTS(Instant lastCallBackOKTS) {
        this.lastOK = lastCallBackOKTS;
    }

    // For debugging
    @SuppressWarnings("UnusedDeclaration")
    static void printStatus(JRTClientConfigRequest request, String message) {
        final String name = request.getConfigKey().getName();
        if (name.equals("components") || name.equals("chains")) {
            log.log(INFO, message + ":" + name + ":" + ", request=" + request);
        }
    }
}
