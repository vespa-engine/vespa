// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.routing.RetryTransientErrorsPolicy;
import com.yahoo.vespaclient.config.FeederConfig;


/**
 * Just a wrapper for feeder options, from config or HTTP parameters.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class FeederOptions {
    // These default values are here basically just for convenience in test cases,
    // they are overridden by real config values in all other cases.
    private boolean abortOnDocumentError = true;
    private boolean abortOnSendError = true;
    private boolean retryEnabled = true;
    private double retryDelay = 1;
    private double timeout = 60;
    private int maxPendingBytes = 0;
    private int maxPendingDocs = 0;
    private double maxFeedRate = 0.0;
    private String documentManagerConfigId = "client";
    private String idPrefix = "";
    private String route = "default";
    private String routingConfigId;
    private String slobrokConfigId;
    private int traceLevel;
    private int mbusPort;
    private DocumentProtocol.Priority priority = DocumentProtocol.Priority.NORMAL_3;
    private boolean priorityExplicitlySet = false;
    private String docprocChain = "";

    /** Constructs an options object with all default values. */
    public FeederOptions() {
        // empty
    }

    /**
     * Implements the copy constructor.
     *
     * @param src The options to copy.
     */
    public FeederOptions(FeederOptions src) {
        abortOnDocumentError = src.abortOnDocumentError;
        abortOnSendError = src.abortOnSendError;
        retryEnabled = src.retryEnabled;
        retryDelay = src.retryDelay;
        timeout = src.timeout;
        maxPendingBytes = src.maxPendingBytes;
        maxPendingDocs = src.maxPendingDocs;
        maxFeedRate = src.maxFeedRate;
        documentManagerConfigId = src.documentManagerConfigId;
        idPrefix = src.idPrefix;
        route = src.route;
        routingConfigId = src.routingConfigId;
        slobrokConfigId = src.slobrokConfigId;
        traceLevel = src.traceLevel;
        mbusPort = src.mbusPort;
        priority = src.priority;
        docprocChain = src.docprocChain;
    }

    /** Constructor that sets values from config. */
    public FeederOptions(FeederConfig config) {
        setAbortOnDocumentError(config.abortondocumenterror());
        setAbortOnSendError(config.abortonsenderror());
        setIdPrefix(config.idprefix());
        setMaxPendingBytes(config.maxpendingbytes());
        setMaxPendingDocs(config.maxpendingdocs());
        setRetryEnabled(config.retryenabled());
        setRetryDelay(config.retrydelay());
        setRoute(config.route());
        setTimeout(config.timeout());
        setTraceLevel(config.tracelevel());
        setMessageBusPort(config.mbusport());
        setDocprocChain(config.docprocchain());
        setMaxFeedRate(config.maxfeedrate());
    }

    public void setMaxFeedRate(double feedRate) {
        maxFeedRate = feedRate;
    }


    public double getMaxFeedRate() {
        return maxFeedRate;
    }

    public boolean getRetryEnabled() {
        return retryEnabled;
    }

    public void setRetryEnabled(boolean retryEnabled) {
        this.retryEnabled = retryEnabled;
    }

    public double getRetryDelay() {
        return retryDelay;
    }

    public void setRetryDelay(double retryDelay) {
        this.retryDelay = retryDelay;
    }

    public double getTimeout() {
        return timeout;
    }

    public void setTimeout(double timeout) {
        this.timeout = timeout;
    }

    public int getMaxPendingBytes() {
        return maxPendingBytes;
    }

    public void setMaxPendingBytes(int maxPendingBytes) {
        this.maxPendingBytes = maxPendingBytes;
    }

    public int getMaxPendingDocs() {
        return maxPendingDocs;
    }

    public void setMaxPendingDocs(int maxPendingDocs) {
        this.maxPendingDocs = maxPendingDocs;
    }

    public boolean abortOnDocumentError() {
        return abortOnDocumentError;
    }

    public void setAbortOnDocumentError(boolean abortOnDocumentError) {
        this.abortOnDocumentError = abortOnDocumentError;
    }

    public boolean abortOnSendError() {
        return abortOnSendError;
    }

    public void setAbortOnSendError(boolean abortOnSendError) {
        this.abortOnSendError = abortOnSendError;
    }

    public String getIdPrefix() {
        return idPrefix;
    }

    public void setIdPrefix(String idPrefix) {
        this.idPrefix = idPrefix;
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

    public boolean isPriorityExplicitlySet() {
        return priorityExplicitlySet;
    }

    public String getSlobrokConfigId() {
        return slobrokConfigId;
    }

    public void setSlobrokConfigId(String slobrokConfigId) {
        this.slobrokConfigId = slobrokConfigId;
    }

    public String getRoutingConfigId() {
        return routingConfigId;
    }

    public void setRoutingConfigId(String routingConfigId) {
        this.routingConfigId = routingConfigId;
    }

    public String getDocumentManagerConfigId() {
        return documentManagerConfigId;
    }

    public void setDocumentManagerConfigId(String documentManagerConfigId) {
        this.documentManagerConfigId = documentManagerConfigId;
    }

    public int getTraceLevel() {
        return traceLevel;
    }

    public void setTraceLevel(int traceLevel) {
        this.traceLevel = traceLevel;
    }

    public int getMessageBusPort() {
        return mbusPort;
    }

    public void setMessageBusPort(int mbusPort) {
        this.mbusPort = mbusPort;
    }

    public void setPriority(DocumentProtocol.Priority priority) {
        this.priority = priority;
        this.priorityExplicitlySet = true;
    }

    public String getDocprocChain() {
        return docprocChain;
    }

    public void setDocprocChain(String chain) {
        docprocChain = chain;
    }

    /**
     * Creates a source session params object with parameters set as these options
     * dictate.
     */
    public SourceSessionParams toSourceSessionParams() {
        SourceSessionParams params = new SourceSessionParams();

        StaticThrottlePolicy policy;
        if (maxFeedRate > 0.0) {
            policy = new RateThrottlingPolicy(maxFeedRate);
        } else if ((maxPendingDocs == 0) && (maxPendingBytes == 0)) {
            policy = new DynamicThrottlePolicy();
        } else {
            policy = new StaticThrottlePolicy();
        }
        if (maxPendingDocs > 0) {
            policy.setMaxPendingCount(maxPendingDocs);
        }
        if (maxPendingBytes > 0) {
            policy.setMaxPendingSize(maxPendingBytes);
        }

        params.setThrottlePolicy(policy);

        params.setTimeout(getTimeout());
        return params;
    }

    public RPCNetworkParams getNetworkParams() {
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
               ", retryDelay=" + retryDelay +
               ", timeout=" + timeout +
               ", maxPendingBytes=" + maxPendingBytes +
               ", maxPendingDocs=" + maxPendingDocs +
               ", documentManagerConfigId='" + documentManagerConfigId + '\'' +
               ", idPrefix='" + idPrefix + '\'' +
               ", route='" + route + '\'' +
               ", routingConfigId='" + routingConfigId + '\'' +
               ", slobrokConfigId='" + slobrokConfigId + '\'' +
               ", traceLevel=" + traceLevel +
               ", mbusPort=" + mbusPort +
               ", priority=" + priority.name() +
               ", priorityExplicitlySet=" + priorityExplicitlySet +
               ", docprocChain='" + docprocChain + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeederOptions)) return false;

        FeederOptions that = (FeederOptions) o;

        if (abortOnDocumentError != that.abortOnDocumentError) return false;
        if (abortOnSendError != that.abortOnSendError) return false;
        if (maxPendingBytes != that.maxPendingBytes) return false;
        if (maxPendingDocs != that.maxPendingDocs) return false;
        if (maxFeedRate != that.maxFeedRate) return false;
        if (mbusPort != that.mbusPort) return false;
        if (priorityExplicitlySet != that.priorityExplicitlySet) return false;
        if (Double.compare(that.retryDelay, retryDelay) != 0) return false;
        if (retryEnabled != that.retryEnabled) return false;
        if (Double.compare(that.timeout, timeout) != 0) return false;
        if (traceLevel != that.traceLevel) return false;
        if (docprocChain != null ? !docprocChain.equals(that.docprocChain) : that.docprocChain != null) return false;
        if (documentManagerConfigId != null ? !documentManagerConfigId.equals(that.documentManagerConfigId) : that.documentManagerConfigId != null) {
            return false;
        }
        if (idPrefix != null ? !idPrefix.equals(that.idPrefix) : that.idPrefix != null) return false;
        if (priority != that.priority) return false;
        if (route != null ? !route.equals(that.route) : that.route != null) return false;
        if (routingConfigId != null ? !routingConfigId.equals(that.routingConfigId) : that.routingConfigId != null) {
            return false;
        }
        if (slobrokConfigId != null ? !slobrokConfigId.equals(that.slobrokConfigId) : that.slobrokConfigId != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = (abortOnDocumentError ? 1 : 0);
        result = 31 * result + (abortOnSendError ? 1 : 0);
        result = 31 * result + (retryEnabled ? 1 : 0);
        temp = retryDelay != +0.0d ? Double.doubleToLongBits(retryDelay) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        temp = timeout != +0.0d ? Double.doubleToLongBits(timeout) : 0L;
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        result = 31 * result + maxPendingBytes;
        result = 31 * result + maxPendingDocs;
        result = 31 * result + ((int)(maxFeedRate * 1000));
        result = 31 * result + (documentManagerConfigId != null ? documentManagerConfigId.hashCode() : 0);
        result = 31 * result + (idPrefix != null ? idPrefix.hashCode() : 0);
        result = 31 * result + (route != null ? route.hashCode() : 0);
        result = 31 * result + (routingConfigId != null ? routingConfigId.hashCode() : 0);
        result = 31 * result + (slobrokConfigId != null ? slobrokConfigId.hashCode() : 0);
        result = 31 * result + traceLevel;
        result = 31 * result + mbusPort;
        result = 31 * result + (priority != null ? priority.hashCode() : 0);
        result = 31 * result + (priorityExplicitlySet ? 1 : 0);
        result = 31 * result + (docprocChain != null ? docprocChain.hashCode() : 0);
        return result;
    }
}
