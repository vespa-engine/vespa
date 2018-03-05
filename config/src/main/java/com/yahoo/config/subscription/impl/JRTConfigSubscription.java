// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.subscription.impl;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.config.subscription.ConfigSource;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.vespa.config.ConfigPayload;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.TimingValues;
import com.yahoo.vespa.config.protocol.CompressionType;
import com.yahoo.vespa.config.protocol.JRTClientConfigRequest;
import com.yahoo.vespa.config.protocol.Payload;

/**
 * A JRT config subscription uses one {@link JRTConfigRequester} to fetch config using Vespa RPC from a config source, typically proxy or server
 *
 * @author vegardh
 * @since 5.1
 */
public class JRTConfigSubscription<T extends ConfigInstance> extends ConfigSubscription<T> {
    private JRTConfigRequester requester;
    private TimingValues timingValues;
    // Last time we got an OK JRT callback for this
    private long lastOK=0;

    /**
     * The queue containing either nothing or the one (newest) request that has got callback from JRT,
     * but has not yet been handled.
     */
    private LinkedBlockingQueue<JRTClientConfigRequest> reqQueue = new LinkedBlockingQueue<>();
    private ConfigSourceSet sources;

    public JRTConfigSubscription(ConfigKey<T> key, ConfigSubscriber subscriber, ConfigSource source, TimingValues timingValues) {
        super(key, subscriber);
        this.timingValues=timingValues;
        if (source instanceof ConfigSourceSet) {
            this.sources=(ConfigSourceSet) source;
        }
    }

    @Override
    public boolean nextConfig(long timeoutMillis) {
        // These flags may have been left true from a previous call, since ConfigSubscriber's nextConfig
        // not necessarily returned true and reset the flags then
        ConfigState<T> configState = getConfigState();
        boolean gotNew = configState.isGenerationChanged() || configState.isConfigChanged() || hasException();
        // Return that now, if there's nothing in queue, so that ConfigSubscriber can move on to other subscriptions to check
        if (getReqQueue().peek()==null && gotNew) {
            return true;
        }
        // Otherwise poll the queue for another generation or timeout
        //
        // Note: since the JRT callback thread will clear the queue first when it inserts a brand new element,
        // there is a race here. However: the caller will handle it no matter what it gets from the queue here,
        // the important part is that local state on the subscription objects is preserved.
        if (!pollQueue(timeoutMillis)) return gotNew;
        configState = getConfigState();
        gotNew = configState.isGenerationChanged() || configState.isConfigChanged() || hasException();
        return gotNew;
    }

    /**
     * Polls the callback queue and <em>maybe</em> sets the following (caller must check): generation, generation changed, config, config changed
     * Important: it never <em>resets</em> those flags, we must persist that state until the {@link ConfigSubscriber} clears it
     * @param timeoutMillis timeout when polling (returns after at most this time)
     * @return true if it got anything off the queue and <em>maybe</em> changed any state, false if timed out taking from queue
     */
    private boolean pollQueue(long timeoutMillis) {
        JRTClientConfigRequest jrtReq;
        try {
            // Only valid responses are on queue, no need to validate
            jrtReq = getReqQueue().poll(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e1) {
            throw new ConfigInterruptedException(e1);
        }
        if (jrtReq == null) {
            // timed out, we know nothing new.
            return false;
        }
        if (jrtReq.hasUpdatedGeneration()) {
            if (jrtReq.hasUpdatedConfig()) {
                setNewConfig(jrtReq);
            } else {
                setGeneration(jrtReq.getNewGeneration());
            }
        }
        return true;
    }

    protected void setNewConfig(JRTClientConfigRequest jrtReq) {
        Exception badConfigE = null;
        T configInstance = null;
        try {
            configInstance = toConfigInstance(jrtReq);
        } catch (IllegalArgumentException e) {
            badConfigE = e;
        }
        setConfig(jrtReq.getNewGeneration(), configInstance);
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
        configInstance.setConfigMd5(jrtRequest.getNewConfigMd5());
        return configInstance;
    }

    LinkedBlockingQueue<JRTClientConfigRequest> getReqQueue() {
        return reqQueue;
    }

    @Override
    public boolean subscribe(long timeout) {
        lastOK=System.currentTimeMillis();
        requester = getRequester();
        requester.request(this);
        JRTClientConfigRequest req = reqQueue.peek();
        while (req == null && (System.currentTimeMillis() - lastOK <= timeout)) {
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
        if (requester==null) {
            requester = new JRTConfigRequester(new JRTConnectionPool(sources), timingValues);
            subscriber.requesters().put(sources, requester);
        }
        return requester;
    }

    @Override
    @SuppressWarnings("serial")
    public void close() {
        super.close();
        reqQueue = new LinkedBlockingQueue<JRTClientConfigRequest>() {
            @Override public void put(JRTClientConfigRequest e) throws InterruptedException {
                // When closed, throw away all requests that callbacks try to put
            }
        };
    }

    /**
     * The timing values of this
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
        log.log(LogLevel.DEBUG, "reload() is without effect on a JRTConfigSubscription.");
    }

    void setLastCallBackOKTS(long lastCallBackOKTS) {
        this.lastOK = lastCallBackOKTS;
    }

    // For debugging
    @SuppressWarnings("UnusedDeclaration")
    static void printStatus(JRTClientConfigRequest request, String message) {
        final String name = request.getConfigKey().getName();
        if (name.equals("components") || name.equals("chains")) {
            log.log(LogLevel.INFO, message + ":" + name + ":" + ", request=" + request);
        }
    }
}
