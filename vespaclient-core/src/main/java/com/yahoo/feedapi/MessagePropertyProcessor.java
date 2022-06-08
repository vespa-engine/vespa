// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.concurrent.SystemTimer;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.vespaclient.config.FeederConfig;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for assigning properties to messages, either from implicit
 * config values or from explicit values in requests.
 */
public class MessagePropertyProcessor implements ConfigSubscriber.SingleSubscriber<FeederConfig> {

    private static final Logger log = Logger.getLogger(MessagePropertyProcessor.class.getName());
    private static final boolean defaultCreateIfNonExistent = false;

    private FeederOptions feederOptions = null;
    private Route defaultRoute = null;
    private long defaultTimeoutMillis = 0;
    private boolean retryEnabled = true;
    private String defaultDocprocChain = null;
    private boolean defaultAbortOnDocumentError = true;
    private boolean defaultAbortOnSendError = true;
    private boolean configChanged = false;

    public MessagePropertyProcessor(FeederConfig config) {
        configure(config);
    }

    public void setRoute(String routeOverride) {
        defaultRoute = Route.parse(routeOverride);
    }

    public PropertySetter buildPropertySetter(HttpRequest request) {
        String routeParam = null;
        double timeoutParam = -1;
        String priorityParam = null;
        String abortOnDocErrorParam = null;
        String abortOnFeedErrorParam = null;
        String traceStr = null;
        String createIfNonExistentParam = null;
        Double totalTimeoutParam = null;

        if (request != null) {
            routeParam = request.getProperty("route");

            String timeoutStr = request.getProperty("timeout");
            if (timeoutStr != null) {
                timeoutParam = Double.parseDouble(timeoutStr);
            }
            timeoutStr = request.getProperty("totaltimeout");
            if (timeoutStr != null) {
                totalTimeoutParam = Double.parseDouble(timeoutStr);
            }

            priorityParam = request.getProperty("priority");
            traceStr = request.getProperty("tracelevel");
            abortOnDocErrorParam = request.getProperty("abortondocumenterror");
            abortOnFeedErrorParam = request.getProperty("abortonfeederror");
            createIfNonExistentParam = request.getProperty("createifnonexistent");
        }

        Route route = (routeParam != null ? Route.parse(routeParam) : null);
        long timeout;
        boolean retry;
        boolean abortOnDocumentError;
        boolean abortOnFeedError;
        boolean createIfNonExistent;

        synchronized (this) {
            if (route == null) {
                route = defaultRoute;
            }
            timeout = (timeoutParam < 0 ? defaultTimeoutMillis : (long)(timeoutParam * 1000));
            retry = retryEnabled;
            abortOnDocumentError = (abortOnDocErrorParam == null ? defaultAbortOnDocumentError : (!"false".equals(abortOnDocErrorParam)));
            abortOnFeedError = (abortOnFeedErrorParam == null ? defaultAbortOnSendError : (!"false".equals(abortOnFeedErrorParam)));
            createIfNonExistent = (createIfNonExistentParam == null ? defaultCreateIfNonExistent : ("true".equals(createIfNonExistentParam)));
        }
        long totalTimeout = (totalTimeoutParam == null) ? timeout : (long)(totalTimeoutParam*1000);

        DocumentProtocol.Priority priority = null;
        if (priorityParam != null) {
            priority = DocumentProtocol.getPriorityByName(priorityParam);
        }

        return new PropertySetter(route, timeout, totalTimeout, priority, retry, abortOnDocumentError, abortOnFeedError, createIfNonExistent, traceStr != null ? Integer.parseInt(traceStr) : 0);
    }

    public long getDefaultTimeoutMillis() { return defaultTimeoutMillis; }
    
    synchronized boolean configChanged() {
        return configChanged;
    }

    synchronized void setConfigChanged(boolean configChanged) {
        this.configChanged = configChanged;
    }

    synchronized FeederOptions getFeederOptions() {
        return feederOptions;
    }

    public synchronized void configure(FeederConfig config) {
        if (feederOptions != null) {
            setConfigChanged(true);
        }

        feederOptions = new FeederOptions(config);
        if (feederOptions.getRoute() != null) {
            defaultRoute = Route.parse(feederOptions.getRoute());
        } else {
            defaultRoute = null;
        }
        defaultTimeoutMillis = (long) (feederOptions.getTimeout() * 1000);
        retryEnabled = feederOptions.getRetryEnabled();
        defaultAbortOnDocumentError = feederOptions.abortOnDocumentError();
        defaultAbortOnSendError = feederOptions.abortOnSendError();

        if (log.isLoggable(Level.FINE)) {
            log.log(Level.FINE, "Received new config (" +
                                    "route: " + (defaultRoute != null ? defaultRoute : "<none>") +
                                    ", timeout: " + defaultTimeoutMillis + " ms, retry enabled: " + retryEnabled +
                                    ", docproc chain: " + (defaultDocprocChain != null ? defaultDocprocChain : "<none>") +
                                    ", abort on doc error: " + defaultAbortOnDocumentError +
                                    ", abort on feed error: " + defaultAbortOnSendError + ")");
        }
    }

    public class PropertySetter implements MessageProcessor {
        /** Route either set by configuration or by explicit request override. May be null */
        private Route route;
        /** Timeout (in milliseconds) */
        private long timeout;
        private long totalTimeout;
        private long startTime;
        /** Explicit priority set. May be null */
        private DocumentProtocol.Priority priority;
        private boolean retryEnabled;
        private boolean abortOnDocumentError;
        private boolean abortOnFeedError;
        private boolean createIfNonExistent;
        private int traceLevel;

        PropertySetter(Route route, long timeout, long totalTimeout, DocumentProtocol.Priority priority,
                       boolean retryEnabled, boolean abortOnDocumentError, boolean abortOnFeedError,
                       boolean createIfNonExistent, int traceLevel) {
            this.route = route;
            this.timeout = timeout;
            this.totalTimeout = totalTimeout;
            this.priority = priority;
            this.retryEnabled = retryEnabled;
            this.abortOnDocumentError = abortOnDocumentError;
            this.abortOnFeedError = abortOnFeedError;
            this.createIfNonExistent = createIfNonExistent;
            this.traceLevel = traceLevel;
            this.startTime = SystemTimer.INSTANCE.milliTime();
        }

        private long getTimeRemaining() {
            return (totalTimeout < 0L)
                    ? timeout
                    : Math.min(timeout, totalTimeout - (SystemTimer.INSTANCE.milliTime() - startTime));
        }

        public Route getRoute() {
            return route;
        }

        public void setRoute(Route route) {
            this.route = route;
        }

        public DocumentProtocol.Priority getPriority() {
            return priority;
        }

        public void setPriority(DocumentProtocol.Priority priority) {
            this.priority = priority;
        }

        public boolean getAbortOnDocumentError() {
            return abortOnDocumentError;
        }

        public boolean getAbortOnFeedError() {
            return abortOnFeedError;
        }

        public boolean getCreateIfNonExistent() {
            return createIfNonExistent;
        }

        @Override
        @SuppressWarnings("removal") // TODO Vespa 9: remove
        public void process(Message msg) {
            if (route != null) {
                msg.setRoute(route);
            }
            msg.setTimeRemaining(getTimeRemaining());
            msg.setRetryEnabled(retryEnabled);
            msg.getTrace().setLevel(Math.max(getFeederOptions().getTraceLevel(), traceLevel));

            if (priority != null) {
                ((DocumentMessage) msg).setPriority(priority);
            }
        }

    }

}
