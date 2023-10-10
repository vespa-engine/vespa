// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.DynamicThrottlePolicy;
import com.yahoo.messagebus.RateThrottlingPolicy;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.vespaclient.config.FeederConfig;

/**
 * A wrapper for feeder options, from config or HTTP parameters.
 *
 * @author Einar M R Rosenvinge
 */
public class FeederOptions {

    // These default values are here basically just for convenience in test cases,
    // they are overridden by real config values in all other cases.
    private boolean abortOnDocumentError = true;
    private boolean abortOnSendError = true;
    private boolean retryEnabled = true;
    private double timeout = 60;
    private int maxPendingDocs = 0;
    private double maxFeedRate = 0.0;
    private String route = "default";
    private int traceLevel;
    private int mbusPort;
    private DocumentProtocol.Priority priority = DocumentProtocol.Priority.NORMAL_3;

    /** Constructs an options object with all default values. */
    FeederOptions() {
        // empty
    }

    /** Constructor that sets values from config. */
    FeederOptions(FeederConfig config) {
        setAbortOnDocumentError(config.abortondocumenterror());
        setAbortOnSendError(config.abortonsenderror());
        setMaxPendingDocs(config.maxpendingdocs());
        setRetryEnabled(config.retryenabled());
        setRoute(config.route());
        setTimeout(config.timeout());
        setTraceLevel(config.tracelevel());
        setMessageBusPort(config.mbusport());
        setMaxFeedRate(config.maxfeedrate());
    }

    void setMaxFeedRate(double feedRate) {
        maxFeedRate = feedRate;
    }

    boolean getRetryEnabled() {
        return retryEnabled;
    }

    private void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    public double getTimeout() {
        return timeout;
    }

    public void setTimeout(double timeout) {
        this.timeout = timeout;
    }

    private void setMaxPendingDocs(int maxPendingDocs) {
        this.maxPendingDocs = maxPendingDocs;
    }

    boolean abortOnDocumentError() {
        return abortOnDocumentError;
    }

    void setAbortOnDocumentError(boolean abortOnDocumentError) {
        this.abortOnDocumentError = abortOnDocumentError;
    }

    boolean abortOnSendError() {
        return abortOnSendError;
    }

    private void setAbortOnSendError(boolean abortOnSendError) {
        this.abortOnSendError = abortOnSendError;
    }

    public void setRoute(String route) {
        this.route = route;
    }

    public String getRoute() {
        return route;
    }

    public DocumentProtocol.Priority getPriority() {
        return priority;
    }

    int getTraceLevel() {
        return traceLevel;
    }

    public void setTraceLevel(int traceLevel) {
        this.traceLevel = traceLevel;
    }

    private void setMessageBusPort(int mbusPort) {
        this.mbusPort = mbusPort;
    }

    public void setPriority(DocumentProtocol.Priority priority) {
        this.priority = priority;
    }

    /**
     * Creates a source session params object with parameters set as these options
     * dictate.
     */
    SourceSessionParams toSourceSessionParams() {
        SourceSessionParams params = new SourceSessionParams();

        StaticThrottlePolicy policy;
        if (maxFeedRate > 0.0) {
            policy = new RateThrottlingPolicy(maxFeedRate);
        } else if (maxPendingDocs == 0) {
            policy = new DynamicThrottlePolicy();
        } else {
            policy = new StaticThrottlePolicy();
        }
        if (maxPendingDocs > 0) {
            policy.setMaxPendingCount(maxPendingDocs);
        }

        params.setThrottlePolicy(policy);

        params.setTimeout(getTimeout());
        return params;
    }

    RPCNetworkParams getNetworkParams() {
        try {
            RPCNetworkParams networkParams = new RPCNetworkParams();
            if (mbusPort != -1) {
                networkParams.setListenPort(mbusPort);
            }
            return networkParams;
        } catch (Exception e) {
        }

        return null;
    }

    @Override
    public String toString() {
        return "FeederOptions{" +
               "abortOnDocumentError=" + abortOnDocumentError +
               ", abortOnSendError=" + abortOnSendError +
               ", retryEnabled=" + retryEnabled +
               ", timeout=" + timeout +
               ", maxPendingDocs=" + maxPendingDocs +
               ", route='" + route + '\'' +
               ", traceLevel=" + traceLevel +
               ", mbusPort=" + mbusPort +
               ", priority=" + priority.name() +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeederOptions)) return false;

        FeederOptions that = (FeederOptions) o;

        if (abortOnDocumentError != that.abortOnDocumentError) return false;
        if (abortOnSendError != that.abortOnSendError) return false;
        if (maxPendingDocs != that.maxPendingDocs) return false;
        if (maxFeedRate != that.maxFeedRate) return false;
        if (mbusPort != that.mbusPort) return false;
        if (retryEnabled != that.retryEnabled) return false;
        if (Double.compare(that.timeout, timeout) != 0) return false;
        if (traceLevel != that.traceLevel) return false;
        if (priority != that.priority) return false;
        if (route != null ? !route.equals(that.route) : that.route != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (abortOnDocumentError ? 1 : 0);
        result = 31 * result + (abortOnSendError ? 1 : 0);
        result = 31 * result + (retryEnabled ? 1 : 0);
        temp = timeout != +0.0d ? Double.doubleToLongBits(timeout) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + maxPendingDocs;
        result = 31 * result + ((int)(maxFeedRate * 1000));
        result = 31 * result + (route != null ? route.hashCode() : 0);
        result = 31 * result + traceLevel;
        result = 31 * result + mbusPort;
        result = 31 * result + (priority != null ? priority.hashCode() : 0);
        return result;
    }
}
