// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.ConfigInterruptedException;
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
 * A config subscription for a config instance, gets config using RPC from a config source
 * (config proxy or config server).
 *
 * @author vegardh
 */
public class JRTConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {

    private final JRTConfigRequester requester;
    private final TimingValues timingValues;

    // Last time we got an OK JRT callback
    private Instant lastOK = Instant.MIN;

    /**
     * A queue containing responses (requests that got a callback from JRT) that has not yet been handled.
     */
    private BlockingQueue<JRTClientConfigRequest> responseQueue = new LinkedBlockingQueue<>();

    public JRTConfigSubscription(ConfigKey<T> key, JRTConfigRequester requester, TimingValues timingValues) {
        super(key);
        this.timingValues = timingValues;
        this.requester = requester;
    }

    @Override
    public boolean nextConfig(long timeoutMillis) {
        JRTClientConfigRequest response = pollForNewConfig(timeoutMillis);
        if (response == null) return newConfigOrException();

        log.log(FINE, () -> "Polled queue and found config " + response);
        // Might set the following (caller must check):
        // generation, generation changed, config, config changed
        // Important: it never <em>resets</em> those flags, we must persist that state until the
        // ConfigSubscriber clears it
        if (response.hasUpdatedGeneration()) {
            setApplyOnRestart(response.responseIsApplyOnRestart());
            if (response.hasUpdatedConfig()) {
                setNewConfig(response);
            } else {
                setNewConfigAndGeneration(response);
            }
        }

        return newConfigOrException();
    }

    private synchronized JRTClientConfigRequest pollForNewConfig(long timeoutMillis) {
        JRTClientConfigRequest response = pollQueue(timeoutMillis);
        // There might be more than one response on the queue, so empty queue by polling with
        // 0 timeout until queue is empty (returned value is null)
        JRTClientConfigRequest temp = response;
        while (temp != null) {
            temp = pollQueue(0);
            if (temp != null)
                response = temp;
        }

        return response;
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
            return responseQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e1) {
            throw new ConfigInterruptedException(e1);
        }
    }

    protected void setNewConfig(JRTClientConfigRequest jrtReq) {
        try {
            T configInstance = toConfigInstance(jrtReq);
            setConfig(jrtReq.getNewGeneration(), jrtReq.responseIsApplyOnRestart(), configInstance, jrtReq.getNewChecksums());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Bad config in response", e);
        }
    }

    protected void setNewConfigAndGeneration(JRTClientConfigRequest jrtReq) {
        try {
            T configInstance = toConfigInstance(jrtReq);
            setConfigAndGeneration(jrtReq.getNewGeneration(),
                                   jrtReq.responseIsApplyOnRestart(),
                                   configInstance,
                                   jrtReq.getNewChecksums());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Bad config in response", e);
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
        return configInstance;
    }

    // Called by JRTConfigRequester when there is a config response for this subscription
    void updateConfig(JRTClientConfigRequest jrtReq) {
        if ( ! responseQueue.offer(jrtReq))
            setException(new ConfigurationRuntimeException("Failed offering returned request to queue of subscription " + this));
    }

    @Override
    public boolean subscribe(long timeout) {
        lastOK = Instant.now();
        requester.request(this);
        JRTClientConfigRequest req = responseQueue.peek();
        while (req == null && (Instant.now().isBefore(lastOK.plus(Duration.ofMillis(timeout))))) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new ConfigInterruptedException(e);
            }
            req = responseQueue.peek();
        }
        return req != null;
    }

    @Override
    public void close() {
        super.close();
        responseQueue = new LinkedBlockingQueue<>() {
            @SuppressWarnings("NullableProblems")
            @Override
            public void put(JRTClientConfigRequest e) {
                // When closed, throw away all responses that callbacks try to put on queue
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
