// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.messagebus.network.Identity;
import com.yahoo.cloud.config.SlobroksConfig;

/**
 * To facilitate several configuration parameters to the {@link RPCNetwork} constructor, all parameters are held by this
 * class. This class has reasonable default values for each parameter.
 *
 * @author Simon Thoresen Hult
 */
public class RPCNetworkParams {

    private Identity identity = new Identity("");
    private String slobrokConfigId = "admin/slobrok.0";
    private SlobroksConfig slobroksConfig = null;
    private int listenPort = 0;
    private int maxInputBufferSize = 256 * 1024;
    private int maxOutputBufferSize = 256 * 1024;
    private double connectionExpireSecs = 30;
    private int numTargetsPerSpec = 1;
    private int numNetworkThreads = 2;

    private int transportEventsBeforeWakeup = 1;
    public enum Optimization {LATENCY, THROUGHPUT}
    Optimization optimization = Optimization.LATENCY;

    /**
     * Constructs a new instance of this class with reasonable default values.
     */
    public RPCNetworkParams() {
        // empty
    }

    /**
     * Implements the copy constructor.
     *
     * @param params The object to copy.
     */
    public RPCNetworkParams(RPCNetworkParams params) {
        identity = new Identity(params.identity);
        slobrokConfigId = params.slobrokConfigId;
        slobroksConfig = params.slobroksConfig;
        listenPort = params.listenPort;
        connectionExpireSecs = params.connectionExpireSecs;
        maxInputBufferSize = params.maxInputBufferSize;
        maxOutputBufferSize = params.maxOutputBufferSize;
        numTargetsPerSpec = params.numTargetsPerSpec;
        numNetworkThreads = params.numNetworkThreads;
        optimization = params.optimization;
    }

    /**
     * Returns the identity to use for the network.
     *
     * @return The identity.
     */
    public Identity getIdentity() {
        return identity;
    }

    /**
     * Sets the identity to use for the network.
     *
     * @param identity The new identity.
     * @return This, to allow chaining.
     */
    public RPCNetworkParams setIdentity(Identity identity) {
        this.identity = identity;
        return this;
    }

    /**
     * Returns the config id of the slobrok config.
     *
     * @return The config id.
     */
    public String getSlobrokConfigId() {
        return slobrokConfigId;
    }

    /**
     * Sets the config id of the slobrok config. Setting this to null string will revert to the default slobrok config
     * identifier.
     *
     * @param slobrokConfigId The new config id.
     * @return This, to allow chaining.
     */
    public RPCNetworkParams setSlobrokConfigId(String slobrokConfigId) {
        this.slobrokConfigId = slobrokConfigId;
        return this;
    }

    /**
     * Returns the 'slobroks' config, if set, otherwise null.
     * @return The 'slobroks' config, if set, otherwise null.
     */
    public SlobroksConfig getSlobroksConfig() {
        return slobroksConfig;
    }

    /**
     * Sets the 'slobroks' config object. Setting this to null will revert to self-subscribing using {@link #getSlobrokConfigId}.
     *
     * @param slobroksConfig the new slobroks config to use, or null.
     * @return This, to allow chaining.
     */
    public RPCNetworkParams setSlobroksConfig(SlobroksConfig slobroksConfig) {
        this.slobroksConfig = slobroksConfig;
        return this;
    }

    /**
     * Returns the port to listen to.
     *
     * @return The port.
     */
    public int getListenPort() {
        return listenPort;
    }

    /**
     * Sets the port to listen to.
     *
     * @param listenPort The new port.
     * @return This, to allow chaining.
     */
    public RPCNetworkParams setListenPort(int listenPort) {
        this.listenPort = listenPort;
        return this;
    }

    /**
     * Returns the number of seconds before an idle network connection expires.
     *
     * @return The number of seconds.
     */
    public double getConnectionExpireSecs() {
        return connectionExpireSecs;
    }

    /**
     * Sets the number of seconds before an idle network connection expires.
     *
     * @param secs The number of seconds.
     * @return This, to allow chaining.
     */
    public RPCNetworkParams setConnectionExpireSecs(double secs) {
        this.connectionExpireSecs = secs;
        return this;
    }

    public RPCNetworkParams setNumTargetsPerSpec(int numTargetsPerSpec) {
        this.numTargetsPerSpec = numTargetsPerSpec;
        return this;
    }
    int getNumTargetsPerSpec() {
        return numTargetsPerSpec;
    }

    public RPCNetworkParams setNumNetworkThreads(int numNetworkThreads) {
        this.numNetworkThreads = numNetworkThreads;
        return this;
    }
    int getNumNetworkThreads() {
        return numNetworkThreads;
    }

    public RPCNetworkParams setOptimization(Optimization optimization) {
        this.optimization = optimization;
        return this;
    }
    Optimization getOptimization() {
        return optimization;
    }

    /**
     * Returns the maximum input buffer size allowed for the underlying FNET connection.
     *
     * @return The maximum number of bytes.
     */
    public int getMaxInputBufferSize() {
        return maxInputBufferSize;
    }

    /**
     * Sets the maximum input buffer size allowed for the underlying FNET connection. Using the value 0 means that there
     * is no limit; the connection will not free any allocated memory until it is cleaned up. This might potentially
     * save alot of allocation time.
     *
     * @param maxInputBufferSize The maximum number of bytes.
     * @return This, to allow chaining.
     */
    RPCNetworkParams setMaxInputBufferSize(int maxInputBufferSize) {
        this.maxInputBufferSize = maxInputBufferSize;
        return this;
    }

    /**
     * Returns the maximum output buffer size allowed for the underlying FNET connection.
     *
     * @return The maximum number of bytes.
     */
    int getMaxOutputBufferSize() {
        return maxOutputBufferSize;
    }

    /**
     * Sets the maximum output buffer size allowed for the underlying FNET connection. Using the value 0 means that
     * there is no limit; the connection will not free any allocated memory until it is cleaned up. This might
     * potentially save alot of allocation time.
     *
     * @param maxOutputBufferSize The maximum number of bytes.
     * @return This, to allow chaining.
     */
    RPCNetworkParams setMaxOutputBufferSize(int maxOutputBufferSize) {
        this.maxOutputBufferSize = maxOutputBufferSize;
        return this;
    }

    public int getTransportEventsBeforeWakeup() {
        return transportEventsBeforeWakeup;
    }

    public RPCNetworkParams setTransportEventsBeforeWakeup(int transportEventsBeforeWakeup) {
        this.transportEventsBeforeWakeup = transportEventsBeforeWakeup;
        return this;
    }
}
