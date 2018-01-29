// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.config.subscription.ConfigSubscriber;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.container.Container;
import com.yahoo.docproc.DocprocService;
import com.yahoo.docproc.jdisc.DocumentProcessingHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.messagebus.loadtypes.LoadType;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.documentapi.messagebus.protocol.DocumentMessage;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.vespaclient.config.FeederConfig;

import java.util.logging.Logger;

/**
 * Utility class for assigning properties to messages, either from implicit
 * config values or from explicit values in requests.
 */
public class MessagePropertyProcessor implements ConfigSubscriber.SingleSubscriber<FeederConfig> {

    private static final Logger log = Logger.getLogger(MessagePropertyProcessor.class.getName());
    private FeederOptions feederOptions = null;
    private Route defaultRoute = null;
    private long defaultTimeoutMillis = 0;
    private boolean retryEnabled = true;
    private String defaultDocprocChain = null;
    private boolean defaultAbortOnDocumentError = true;
    private boolean defaultAbortOnSendError = true;
    private boolean defaultCreateIfNonExistent = false;
    private LoadTypeSet loadTypes = null;
    private boolean configChanged = false;

    public MessagePropertyProcessor(String configId, String loadTypeConfig) {
        new ConfigSubscriber().subscribe(this, FeederConfig.class, configId);
        loadTypes = new LoadTypeSet(loadTypeConfig);
    }

    public MessagePropertyProcessor(FeederConfig config, LoadTypeConfig loadTypeCfg) {
        loadTypes = new LoadTypeSet();
        configure(config, loadTypeCfg);
    }

    public void setRoute(String routeOverride) {
        defaultRoute = Route.parse(routeOverride);
    }

    private synchronized String getDocprocChainParameter(HttpRequest request) {
        String docprocChainParam = request.getProperty("docprocchain");
        return (docprocChainParam == null ? defaultDocprocChain : docprocChainParam);
    }

    public synchronized DocprocService getDocprocChain(HttpRequest request) {
        ComponentRegistry<DocprocService> services = getDocprocServiceRegistry(request);

        String docprocChain = getDocprocChainParameter(request);
        if (docprocChain == null) {
            return null;
        }

        return services.getComponent(docprocChain);
    }

    public synchronized ComponentRegistry<DocprocService> getDocprocServiceRegistry(HttpRequest request) {
        String docprocChain = getDocprocChainParameter(request);
        if (docprocChain == null) {
            return null;
        }

        Container container = Container.get();
        if (container == null) {
            throw new IllegalStateException("Could not get Container instance.");
        }

        ComponentRegistry<RequestHandler> requestHandlerRegistry = container.getRequestHandlerRegistry();
        if (requestHandlerRegistry == null) {
            throw new IllegalStateException("Could not get requesthandlerregistry.");
        }

        DocumentProcessingHandler handler = (DocumentProcessingHandler) requestHandlerRegistry
                .getComponent(DocumentProcessingHandler.class.getName());
        if (handler == null) {
            return null;
        }
        ComponentRegistry<DocprocService> services = handler.getDocprocServiceRegistry();
        if (services == null) {
            throw new IllegalStateException("Could not get DocprocServiceRegistry.");
        }
        return services;
    }

    public PropertySetter buildPropertySetter(HttpRequest request) {
        String routeParam = null;
        double timeoutParam = -1;
        String priorityParam = null;
        String abortOnDocErrorParam = null;
        String abortOnFeedErrorParam = null;
        String loadTypeStr = null;
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
            loadTypeStr = request.getProperty("loadtype");
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

        LoadType loadType = null;
        if (loadTypes != null && loadTypeStr != null) {
            loadType = loadTypes.getNameMap().get(loadTypeStr);
        }

        if (loadType == null) {
            loadType = LoadType.DEFAULT;
        }

        return new PropertySetter(route, timeout, totalTimeout, priority, loadType, retry, abortOnDocumentError, abortOnFeedError, createIfNonExistent, traceStr != null ? Integer.parseInt(traceStr) : 0);
    }

    public long getDefaultTimeoutMillis() { return defaultTimeoutMillis; }
    
    public synchronized boolean configChanged() {
        return configChanged;
    }

    public synchronized void setConfigChanged(boolean configChanged) {
        this.configChanged = configChanged;
    }

    public synchronized FeederOptions getFeederOptions() {
        return feederOptions;
    }

    public synchronized void configure(FeederConfig config, LoadTypeConfig loadTypeConfig) {
        loadTypes.configure(loadTypeConfig);
        configure(config);
    }

    public LoadTypeSet getLoadTypes() {
        return loadTypes;
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

        if (!"".equals(feederOptions.getDocprocChain())) {
            defaultDocprocChain = feederOptions.getDocprocChain();
        } else {
            defaultDocprocChain = null;
        }

        if (log.isLoggable(LogLevel.DEBUG)) {
            log.log(LogLevel.DEBUG, "Received new config (" +
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
        private LoadType loadType;
        private int traceLevel;

        public PropertySetter(Route route, long timeout, long totalTimeout, DocumentProtocol.Priority priority, LoadType loadType,
                              boolean retryEnabled, boolean abortOnDocumentError, boolean abortOnFeedError,
                              boolean createIfNonExistent, int traceLevel) {
            this.route = route;
            this.timeout = timeout;
            this.totalTimeout = totalTimeout;
            this.priority = priority;
            this.loadType = loadType;
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

        public long getTimeout() {
            return timeout;
        }

        public void setTimeout(long timeout) {
            this.timeout = timeout;
        }

        public DocumentProtocol.Priority getPriority() {
            return priority;
        }

        public void setPriority(DocumentProtocol.Priority priority) {
            this.priority = priority;
        }

        public LoadType getLoadType() {
            return loadType;
        }

        public void setLoadType(LoadType loadType) {
            this.loadType = loadType;
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
        public void process(Message msg) {
            if (route != null) {
                msg.setRoute(route);
            }
            msg.setTimeRemaining(getTimeRemaining());
            msg.setRetryEnabled(retryEnabled);
            msg.getTrace().setLevel(Math.max(getFeederOptions().getTraceLevel(), traceLevel));

            if (loadType != null) {
                ((DocumentMessage) msg).setLoadType(loadType);
                ((DocumentMessage) msg).setPriority(loadType.getPriority());
            }

            if (priority != null) {
                ((DocumentMessage) msg).setPriority(priority);
            }
        }

        public void process(VisitorParameters params) {
            if (route != null) {
                params.setRoute(route);
            }
            params.setTimeoutMs(timeout);

            params.setTraceLevel(Math.max(getFeederOptions().getTraceLevel(), traceLevel));

            if (loadType != null) {
                params.setLoadType(loadType);
                params.setPriority(loadType.getPriority());
            }

            if (priority != null) {
                params.setPriority(priority);
            }
        }
    }
}
