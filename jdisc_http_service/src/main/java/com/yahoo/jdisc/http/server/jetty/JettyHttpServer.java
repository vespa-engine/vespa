// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.jdisc.http.server.FilterBindings;
import com.yahoo.jdisc.service.AbstractServerProvider;
import com.yahoo.jdisc.service.CurrentContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.ConnectorStatistics;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.RequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandlerContainer;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import javax.servlet.DispatcherType;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.jdisc.http.server.jetty.ConnectorFactory.JDiscServerConnector;
import static com.yahoo.jdisc.http.server.jetty.Exceptions.throwUnchecked;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
@Beta
public class JettyHttpServer extends AbstractServerProvider {

    public interface Metrics {
        final String NAME_DIMENSION = "serverName";
        final String PORT_DIMENSION = "serverPort";

        final String NUM_ACTIVE_REQUESTS = "serverNumActiveRequests";
        final String NUM_OPEN_CONNECTIONS = "serverNumOpenConnections";
        final String NUM_CONNECTIONS_OPEN_MAX = "serverConnectionsOpenMax";
        final String CONNECTION_DURATION_MAX = "serverConnectionDurationMax";
        final String CONNECTION_DURATION_MEAN = "serverConnectionDurationMean";
        final String CONNECTION_DURATION_STD_DEV = "serverConnectionDurationStdDev";

        final String NUM_BYTES_RECEIVED = "serverBytesReceived";
        final String NUM_BYTES_SENT     = "serverBytesSent";
        final String MANHATTAN_NUM_BYTES_RECEIVED = "http.in.bytes";
        final String MANHATTAN_NUM_BYTES_SENT     = "http.out.bytes";

        final String NUM_CONNECTIONS = "serverNumConnections";
        final String NUM_CONNECTIONS_IDLE = "serverNumConnectionsIdle";
        final String NUM_UNEXPECTED_DISCONNECTS = "serverNumUnexpectedDisconnects";

        /* For historical reasons, these are all aliases for the same metric. 'jdisc.http' should ideally be the only one. */
        final String JDISC_HTTP_REQUESTS = "jdisc.http.requests";
        final String NUM_REQUESTS = "serverNumRequests";
        final String MANHATTAN_NUM_REQUESTS = "http.requests";

        final String NUM_SUCCESSFUL_RESPONSES = "serverNumSuccessfulResponses";
        final String NUM_FAILED_RESPONSES = "serverNumFailedResponses";
        final String NUM_SUCCESSFUL_WRITES = "serverNumSuccessfulResponseWrites";
        final String NUM_FAILED_WRITES = "serverNumFailedResponseWrites";

        final String NETWORK_LATENCY = "serverNetworkLatency";
        final String TOTAL_SUCCESSFUL_LATENCY = "serverTotalSuccessfulResponseLatency";
        final String MANHATTAN_TOTAL_SUCCESSFUL_LATENCY = "http.latency";
        final String TOTAL_FAILED_LATENCY = "serverTotalFailedResponseLatency";
        final String TIME_TO_FIRST_BYTE = "serverTimeToFirstByte";
        final String MANHATTAN_TIME_TO_FIRST_BYTE = "http.out.firstbytetime";

        final String RESPONSES_1XX = "http.status.1xx";
        final String RESPONSES_2XX = "http.status.2xx";
        final String RESPONSES_3XX = "http.status.3xx";
        final String RESPONSES_4XX = "http.status.4xx";
        final String RESPONSES_5XX = "http.status.5xx";

        final String STARTED_MILLIS = "serverStartedMillis";
        final String MANHATTAN_STARTED_MILLIS = "proc.uptime";
    }

    private final static Logger log = Logger.getLogger(JettyHttpServer.class.getName());
    private final long timeStarted = System.currentTimeMillis();
    private final ExecutorService janitor;
    private final ScheduledExecutorService metricReporterExecutor;
    private final Metric metric;
    private final Server server;

    @Inject
    public JettyHttpServer(
            final CurrentContainer container,
            final Metric metric,
            final ServerConfig serverConfig,
            final ServletPathsConfig servletPathsConfig,
            final ThreadFactory threadFactory,
            final FilterBindings filterBindings,
            final ComponentRegistry<ConnectorFactory> connectorFactories,
            final ComponentRegistry<ServletHolder> servletHolders,
            final OsgiFramework osgiFramework,
            final FilterInvoker filterInvoker,
            final AccessLog accessLog) {
        super(container);
        if (connectorFactories.allComponents().isEmpty()) {
            throw new IllegalArgumentException("No connectors configured.");
        }
        this.metric = metric;

        server = new Server();
        ((QueuedThreadPool)server.getThreadPool()).setMaxThreads(serverConfig.maxWorkerThreads());

        Map<Path, FileChannel> keyStoreChannels = getKeyStoreFileChannels(osgiFramework.bundleContext());

        for (ConnectorFactory connectorFactory : connectorFactories.allComponents()) {
            ServerSocketChannel preBoundChannel = getChannelFromServiceLayer(connectorFactory.getConnectorConfig().listenPort(), osgiFramework.bundleContext());
            server.addConnector(connectorFactory.createConnector(metric, server, preBoundChannel, keyStoreChannels));
        }

        janitor = newJanitor(threadFactory);

        JDiscContext jDiscContext = new JDiscContext(
                filterBindings.getRequestFilters().activate(),
                filterBindings.getResponseFilters().activate(),
                container,
                janitor,
                metric,
                serverConfig);

        ServletHolder jdiscServlet = new ServletHolder(new JDiscHttpServlet(jDiscContext));
        FilterHolder jDiscFilterInvokerFilter = new FilterHolder(new JDiscFilterInvokerFilter(jDiscContext, filterInvoker));

        final RequestLog requestLog = new AccessLogRequestLog(accessLog);

        server.setHandler(
                getHandlerCollection(
                        serverConfig,
                        servletPathsConfig,
                        jdiscServlet,
                        servletHolders,
                        jDiscFilterInvokerFilter,
                        requestLog));

        final int numMetricReporterThreads = 1;
        metricReporterExecutor = Executors.newScheduledThreadPool(
                numMetricReporterThreads,
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat(JettyHttpServer.class.getName() + "-MetricReporter-%d")
                        .setThreadFactory(threadFactory)
                        .build()
        );
        metricReporterExecutor.scheduleAtFixedRate(new MetricTask(), 0, 2, TimeUnit.SECONDS);
    }

    private HandlerCollection getHandlerCollection(
            ServerConfig serverConfig,
            ServletPathsConfig servletPathsConfig,
            ServletHolder jdiscServlet,
            ComponentRegistry<ServletHolder> servletHolders,
            FilterHolder jDiscFilterInvokerFilter,
            RequestLog requestLog) {

        ServletContextHandler servletContextHandler = createServletContextHandler();

        servletHolders.allComponentsById().forEach((id, servlet) -> {
            String path = getServletPath(servletPathsConfig, id);
            servletContextHandler.addServlet(servlet, path);
            servletContextHandler.addFilter(jDiscFilterInvokerFilter, path, EnumSet.allOf(DispatcherType.class));
        });

        servletContextHandler.addServlet(jdiscServlet, "/*");

        final GzipHandler gzipHandler = newGzipHandler(serverConfig);
        gzipHandler.setHandler(servletContextHandler);

        final StatisticsHandler statisticsHandler = newStatisticsHandler();
        statisticsHandler.setHandler(gzipHandler);

        final RequestLogHandler requestLogHandler = new RequestLogHandler();
        requestLogHandler.setRequestLog(requestLog);

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers(new Handler[]{statisticsHandler, requestLogHandler});
        return handlerCollection;
    }

    private static String getServletPath(ServletPathsConfig servletPathsConfig, ComponentId id) {
        return "/" + servletPathsConfig.servlets(id.stringValue()).path();
    }

    // Ugly trick to get generic type literal.
    @SuppressWarnings("unchecked")
    private static final Class<Map<?, ?>> mapClass = (Class<Map<?, ?>>) (Object) Map.class;

    private Map<Path, FileChannel> getKeyStoreFileChannels(BundleContext bundleContext) {
        try {
            Collection<ServiceReference<Map<?, ?>>> serviceReferences = bundleContext.getServiceReferences(mapClass,
                    "(role=com.yahoo.container.standalone.StandaloneContainerActivator.KeyStoreFileChannels)");

            if (serviceReferences == null || serviceReferences.isEmpty())
                return Collections.emptyMap();

            if (serviceReferences.size() != 1)
                throw new IllegalStateException("Multiple KeyStoreFileChannels registered");

            return getKeyStoreFileChannels(bundleContext, serviceReferences.iterator().next());
        } catch (InvalidSyntaxException e) {
            throw throwUnchecked(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Path, FileChannel> getKeyStoreFileChannels(BundleContext bundleContext, ServiceReference<Map<?, ?>> keyStoreFileChannelReference) {
        Map<?, ?> fileChannelMap = bundleContext.getService(keyStoreFileChannelReference);
        try {
            if (fileChannelMap == null)
                return Collections.emptyMap();

            Map<Path, FileChannel> result = (Map<Path, FileChannel>) fileChannelMap;
            log.fine("Using file channel for " + result.keySet());
            return result;
        } finally {
            //if we change this to be anything other than a simple map, we should hold the reference as long as the object is in use.
            bundleContext.ungetService(keyStoreFileChannelReference);
        }
    }

    private ServletContextHandler createServletContextHandler() {
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath("/");
        return servletContextHandler;
    }

    private ServerSocketChannel getChannelFromServiceLayer(int listenPort, BundleContext bundleContext) {
        log.log(Level.FINE, "Retrieving channel for port " + listenPort + " from " + bundleContext.getClass().getName());
        Collection<ServiceReference<ServerSocketChannel>> refs;
        final String filter = "(port=" + listenPort + ")";
        try {
            refs = bundleContext.getServiceReferences(ServerSocketChannel.class, filter);
        } catch (InvalidSyntaxException e) {
            throw new IllegalStateException("OSGi framework rejected filter " + filter, e);
        }
        if (refs.isEmpty()) {
            return null;
        }
        if (refs.size() != 1) {
            throw new IllegalStateException("Got more than one service reference for " + ServerSocketChannel.class + " port " + listenPort + ".");
        }
        ServiceReference<ServerSocketChannel> ref = refs.iterator().next();
        return bundleContext.getService(ref);
    }

    private static ExecutorService newJanitor(final ThreadFactory factory) {
        final int threadPoolSize = Runtime.getRuntime().availableProcessors();
        log.info("Creating janitor executor with " + threadPoolSize + " threads");
        return Executors.newFixedThreadPool(
                threadPoolSize,
                new ThreadFactoryBuilder()
                        .setDaemon(true)
                        .setNameFormat(JettyHttpServer.class.getName() + "-Janitor-%d")
                        .setThreadFactory(factory)
                        .build()
        );
    }

    @Override
    public void start() {
        try {
            server.start();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to start server.", e);
        }
    }

    @Override
    public void close() {
        try {
            server.stop();
        } catch (final Exception e) {
            log.log(Level.SEVERE, "Server shutdown threw an unexpected exception.", e);
        }

        metricReporterExecutor.shutdown();
        janitor.shutdown();
    }

    public int getListenPort() {
        return ((ServerConnector)server.getConnectors()[0]).getLocalPort();
    }

    private class MetricTask implements Runnable {
        @Override
        public void run() {
            StatisticsHandler statisticsHandler = ((AbstractHandlerContainer)server.getHandler())
                    .getChildHandlerByClass(StatisticsHandler.class);
            if (statisticsHandler == null)
                return;

            setServerMetrics(statisticsHandler);

            for (Connector connector : server.getConnectors()) {
                setConnectorMetrics((JDiscServerConnector)connector);
            }
        }

    }

    private void setServerMetrics(StatisticsHandler statistics) {
        long timeSinceStarted = System.currentTimeMillis() - timeStarted;
        metric.set(Metrics.STARTED_MILLIS, timeSinceStarted, null);
        metric.set(Metrics.MANHATTAN_STARTED_MILLIS, timeSinceStarted, null);

        metric.add(Metrics.RESPONSES_1XX, statistics.getResponses1xx(), null);
        metric.add(Metrics.RESPONSES_2XX, statistics.getResponses2xx(), null);
        metric.add(Metrics.RESPONSES_3XX, statistics.getResponses3xx(), null);
        metric.add(Metrics.RESPONSES_4XX, statistics.getResponses4xx(), null);
        metric.add(Metrics.RESPONSES_5XX, statistics.getResponses5xx(), null);

        // Reset to only add the diff for count metrics.
        // (The alternative to reset would be to preserve the previous value, and only add the diff.)
        statistics.statsReset();
    }

    private void setConnectorMetrics(JDiscServerConnector connector) {
        ConnectorStatistics statistics = connector.getStatistics();
        metric.set(Metrics.NUM_CONNECTIONS, statistics.getConnections(), connector.getMetricContext());
        metric.set(Metrics.NUM_OPEN_CONNECTIONS, statistics.getConnectionsOpen(), connector.getMetricContext());
        metric.set(Metrics.NUM_CONNECTIONS_OPEN_MAX, statistics.getConnectionsOpenMax(), connector.getMetricContext());
        metric.set(Metrics.CONNECTION_DURATION_MAX, statistics.getConnectionDurationMax(), connector.getMetricContext());
        metric.set(Metrics.CONNECTION_DURATION_MEAN, statistics.getConnectionDurationMean(), connector.getMetricContext());
        metric.set(Metrics.CONNECTION_DURATION_STD_DEV, statistics.getConnectionDurationStdDev(), connector.getMetricContext());
    }

    private StatisticsHandler newStatisticsHandler() {
        StatisticsHandler statisticsHandler = new StatisticsHandler();
        statisticsHandler.statsReset();
        return statisticsHandler;
    }

    private GzipHandler newGzipHandler(ServerConfig serverConfig) {
        final GzipHandler gzipHandler = new GzipHandler();
        gzipHandler.setCompressionLevel(serverConfig.responseCompressionLevel());
        gzipHandler.setCheckGzExists(false);
        gzipHandler.setIncludedMethods("GET", "POST");
        return gzipHandler;
    }
}
