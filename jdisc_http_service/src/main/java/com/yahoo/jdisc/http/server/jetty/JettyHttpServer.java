// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.jdisc.http.server.FilterBindings;
import com.yahoo.jdisc.service.AbstractServerProvider;
import com.yahoo.jdisc.service.CurrentContainer;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnectionStatistics;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.AbstractHandlerContainer;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHttpOutputInterceptor;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.JavaUtilLog;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import javax.management.remote.JMXServiceURL;
import javax.servlet.DispatcherType;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.net.MalformedURLException;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
@Beta
public class JettyHttpServer extends AbstractServerProvider {

    public interface Metrics {
        String NAME_DIMENSION = "serverName";
        String PORT_DIMENSION = "serverPort";
        String METHOD_DIMENSION = "httpMethod";
        String SCHEME_DIMENSION = "scheme";

        String NUM_OPEN_CONNECTIONS = "serverNumOpenConnections";
        String NUM_CONNECTIONS_OPEN_MAX = "serverConnectionsOpenMax";
        String CONNECTION_DURATION_MAX = "serverConnectionDurationMax";
        String CONNECTION_DURATION_MEAN = "serverConnectionDurationMean";
        String CONNECTION_DURATION_STD_DEV = "serverConnectionDurationStdDev";
        String NUM_PREMATURELY_CLOSED_CONNECTIONS = "jdisc.http.request.prematurely_closed";

        String NUM_BYTES_RECEIVED = "serverBytesReceived";
        String NUM_BYTES_SENT     = "serverBytesSent";

        String NUM_CONNECTIONS = "serverNumConnections";

        /* For historical reasons, these are all aliases for the same metric. 'jdisc.http' should ideally be the only one. */
        String JDISC_HTTP_REQUESTS = "jdisc.http.requests";
        String NUM_REQUESTS = "serverNumRequests";

        String NUM_SUCCESSFUL_RESPONSES = "serverNumSuccessfulResponses";
        String NUM_FAILED_RESPONSES = "serverNumFailedResponses";
        String NUM_SUCCESSFUL_WRITES = "serverNumSuccessfulResponseWrites";
        String NUM_FAILED_WRITES = "serverNumFailedResponseWrites";

        String TOTAL_SUCCESSFUL_LATENCY = "serverTotalSuccessfulResponseLatency";
        String TOTAL_FAILED_LATENCY = "serverTotalFailedResponseLatency";
        String TIME_TO_FIRST_BYTE = "serverTimeToFirstByte";

        String RESPONSES_1XX = "http.status.1xx";
        String RESPONSES_2XX = "http.status.2xx";
        String RESPONSES_3XX = "http.status.3xx";
        String RESPONSES_4XX = "http.status.4xx";
        String RESPONSES_5XX = "http.status.5xx";
        String RESPONSES_401 = "http.status.401";
        String RESPONSES_403 = "http.status.403";

        String STARTED_MILLIS = "serverStartedMillis";

        String URI_LENGTH = "jdisc.http.request.uri_length";
        String CONTENT_SIZE = "jdisc.http.request.content_size";

        String SSL_HANDSHAKE_FAILURE_MISSING_CLIENT_CERT = "jdisc.http.ssl.handshake.failure.missing_client_cert";
        String SSL_HANDSHAKE_FAILURE_INVALID_CLIENT_CERT = "jdisc.http.ssl.handshake.failure.invalid_client_cert";
        String SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_PROTOCOLS = "jdisc.http.ssl.handshake.failure.incompatible_protocols";
        String SSL_HANDSHAKE_FAILURE_INCOMPATIBLE_CIPHERS = "jdisc.http.ssl.handshake.failure.incompatible_ciphers";
        String SSL_HANDSHAKE_FAILURE_UNKNOWN = "jdisc.http.ssl.handshake.failure.unknown";
    }

    private final static Logger log = Logger.getLogger(JettyHttpServer.class.getName());
    private final long timeStarted = System.currentTimeMillis();
    private final ExecutorService janitor;
    private final ScheduledExecutorService metricReporterExecutor;
    private final Metric metric;
    private final Server server;
    private final List<Integer> listenedPorts = new ArrayList<>();

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
        if (connectorFactories.allComponents().isEmpty())
            throw new IllegalArgumentException("No connectors configured.");
        this.metric = metric;

        initializeJettyLogging();

        server = new Server();
        server.setStopTimeout((long)(serverConfig.stopTimeout() * 1000.0));
        server.setRequestLog(new AccessLogRequestLog(accessLog));
        setupJmx(server, serverConfig);
        ((QueuedThreadPool)server.getThreadPool()).setMaxThreads(serverConfig.maxWorkerThreads());

        List<ConnectorConfig> connectorConfigs = new ArrayList<>();
        for (ConnectorFactory connectorFactory : connectorFactories.allComponents()) {
            ConnectorConfig connectorConfig = connectorFactory.getConnectorConfig();
            connectorConfigs.add(connectorConfig);
            ServerSocketChannel preBoundChannel = getChannelFromServiceLayer(connectorConfig.listenPort(), osgiFramework.bundleContext());
            server.addConnector(connectorFactory.createConnector(metric, server, preBoundChannel));
            listenedPorts.add(connectorConfig.listenPort());
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

        List<JDiscServerConnector> connectors = Arrays.stream(server.getConnectors())
                .map(JDiscServerConnector.class::cast)
                .collect(toList());

        server.setHandler(
                getHandlerCollection(
                        serverConfig,
                        servletPathsConfig,
                        connectors,
                        jdiscServlet,
                        servletHolders,
                        jDiscFilterInvokerFilter));

        int numMetricReporterThreads = 1;
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

    private static void initializeJettyLogging() {
        // Note: Jetty is logging stderr if no logger is explicitly configured.
        try {
            Log.setLog(new JavaUtilLog());
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize logging framework for Jetty");
        }
    }

    private static void setupJmx(Server server, ServerConfig serverConfig) {
        if (serverConfig.jmx().enabled()) {
            System.setProperty("java.rmi.server.hostname", "localhost");
            server.addBean(
                    new MBeanContainer(ManagementFactory.getPlatformMBeanServer()));
            server.addBean(
                    new ConnectorServer(
                            createJmxLoopbackOnlyServiceUrl(serverConfig.jmx().listenPort()),
                            "org.eclipse.jetty.jmx:name=rmiconnectorserver"));
        }
    }

    private static JMXServiceURL createJmxLoopbackOnlyServiceUrl(int port) {
        try {
            return new JMXServiceURL(
                    "rmi", "localhost", port, "/jndi/rmi://localhost:" + port + "/jmxrmi");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private HandlerCollection getHandlerCollection(
            ServerConfig serverConfig,
            ServletPathsConfig servletPathsConfig,
            List<JDiscServerConnector> connectors,
            ServletHolder jdiscServlet,
            ComponentRegistry<ServletHolder> servletHolders,
            FilterHolder jDiscFilterInvokerFilter) {

        ServletContextHandler servletContextHandler = createServletContextHandler();

        servletHolders.allComponentsById().forEach((id, servlet) -> {
            String path = getServletPath(servletPathsConfig, id);
            servletContextHandler.addServlet(servlet, path);
            servletContextHandler.addFilter(jDiscFilterInvokerFilter, path, EnumSet.allOf(DispatcherType.class));
        });

        servletContextHandler.addServlet(jdiscServlet, "/*");

        var proxyHandler = new HealthCheckProxyHandler(connectors);
        proxyHandler.setHandler(servletContextHandler);

        List<ConnectorConfig> connectorConfigs = connectors.stream().map(JDiscServerConnector::connectorConfig).collect(toList());
        var authEnforcer = new TlsClientAuthenticationEnforcer(connectorConfigs);
        authEnforcer.setHandler(proxyHandler);

        GzipHandler gzipHandler = newGzipHandler(serverConfig);
        gzipHandler.setHandler(authEnforcer);

        HttpResponseStatisticsCollector statisticsCollector = new HttpResponseStatisticsCollector();
        statisticsCollector.setHandler(gzipHandler);

        StatisticsHandler statisticsHandler = newStatisticsHandler();
        statisticsHandler.setHandler(statisticsCollector);

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers(new Handler[] { statisticsHandler });
        return handlerCollection;
    }

    private static String getServletPath(ServletPathsConfig servletPathsConfig, ComponentId id) {
        return "/" + servletPathsConfig.servlets(id.stringValue()).path();
    }

    private ServletContextHandler createServletContextHandler() {
        ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        servletContextHandler.setContextPath("/");
        servletContextHandler.setDisplayName(getDisplayName(listenedPorts));
        return servletContextHandler;
    }

    private static String getDisplayName(List<Integer> ports) {
        return ports.stream().map(Object::toString).collect(Collectors.joining(":"));
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

    private static ExecutorService newJanitor(ThreadFactory factory) {
        int threadPoolSize = Runtime.getRuntime().availableProcessors();
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
            logEffectiveSslConfiguration();
        } catch (final Exception e) {
            if (e instanceof IOException && e.getCause() instanceof BindException) {
                throw new RuntimeException("Failed to start server due to BindExecption. ListenPorts = " + listenedPorts.toString(), e.getCause());
            }
            throw new RuntimeException("Failed to start server.", e);
        }
    }

    private void logEffectiveSslConfiguration() {
        if (!server.isStarted()) throw new IllegalStateException();
        for (Connector connector : server.getConnectors()) {
            ServerConnector serverConnector = (ServerConnector) connector;
            int localPort = serverConnector.getLocalPort();
            var sslConnectionFactory = serverConnector.getConnectionFactory(SslConnectionFactory.class);
            if (sslConnectionFactory != null) {
                var sslContextFactory = sslConnectionFactory.getSslContextFactory();
                log.info(String.format("Enabled SSL cipher suites for port '%d': %s",
                                       localPort, Arrays.toString(sslContextFactory.getSelectedCipherSuites())));
                log.info(String.format("Enabled SSL protocols for port '%d': %s",
                                       localPort, Arrays.toString(sslContextFactory.getSelectedProtocols())));
            }
        }
    }

    @Override
    public void close() {
        try {
            log.log(Level.INFO, String.format("Shutting down server (graceful=%b, timeout=%.1fs)", isGracefulShutdownEnabled(), server.getStopTimeout()/1000d));
            server.stop();
            log.log(Level.INFO, "Server shutdown completed");
        } catch (final Exception e) {
            log.log(Level.SEVERE, "Server shutdown threw an unexpected exception.", e);
        }

        metricReporterExecutor.shutdown();
        janitor.shutdown();
    }

    private boolean isGracefulShutdownEnabled() {
        return server.getChildHandlersByClass(StatisticsHandler.class).length > 0 && server.getStopTimeout() > 0;
    }

    public int getListenPort() {
        return ((ServerConnector)server.getConnectors()[0]).getLocalPort();
    }

    private class MetricTask implements Runnable {
        @Override
        public void run() {
            HttpResponseStatisticsCollector statisticsCollector = ((AbstractHandlerContainer) server.getHandler())
                    .getChildHandlerByClass(HttpResponseStatisticsCollector.class);
            if (statisticsCollector != null) {
                setServerMetrics(statisticsCollector);
            }

            // reset statisticsHandler to preserve earlier behavior
            StatisticsHandler statisticsHandler = ((AbstractHandlerContainer) server.getHandler())
                    .getChildHandlerByClass(StatisticsHandler.class);
            if (statisticsHandler != null) {
                statisticsHandler.statsReset();
            }

            for (Connector connector : server.getConnectors()) {
                setConnectorMetrics((JDiscServerConnector)connector);
            }
        }

    }

    private void setServerMetrics(HttpResponseStatisticsCollector statisticsCollector) {
        long timeSinceStarted = System.currentTimeMillis() - timeStarted;
        metric.set(Metrics.STARTED_MILLIS, timeSinceStarted, null);

        addResponseMetrics(statisticsCollector);
    }

    private void addResponseMetrics(HttpResponseStatisticsCollector statisticsCollector) {
        for (var metricEntry : statisticsCollector.takeStatistics()) {
            Map<String, Object> dimensions = new HashMap<>();
            dimensions.put(Metrics.METHOD_DIMENSION, metricEntry.method);
            dimensions.put(Metrics.SCHEME_DIMENSION, metricEntry.scheme);
            metric.add(metricEntry.name, metricEntry.value, metric.createContext(dimensions));
        }
    }

    private void setConnectorMetrics(JDiscServerConnector connector) {
        ServerConnectionStatistics statistics = connector.getStatistics();
        metric.set(Metrics.NUM_CONNECTIONS, statistics.getConnectionsTotal(), connector.getConnectorMetricContext());
        metric.set(Metrics.NUM_OPEN_CONNECTIONS, statistics.getConnections(), connector.getConnectorMetricContext());
        metric.set(Metrics.NUM_CONNECTIONS_OPEN_MAX, statistics.getConnectionsMax(), connector.getConnectorMetricContext());
        metric.set(Metrics.CONNECTION_DURATION_MAX, statistics.getConnectionDurationMax(), connector.getConnectorMetricContext());
        metric.set(Metrics.CONNECTION_DURATION_MEAN, statistics.getConnectionDurationMean(), connector.getConnectorMetricContext());
        metric.set(Metrics.CONNECTION_DURATION_STD_DEV, statistics.getConnectionDurationStdDev(), connector.getConnectorMetricContext());
    }

    private StatisticsHandler newStatisticsHandler() {
        StatisticsHandler statisticsHandler = new StatisticsHandler();
        statisticsHandler.statsReset();
        return statisticsHandler;
    }

    private GzipHandler newGzipHandler(ServerConfig serverConfig) {
        GzipHandler gzipHandler = new GzipHandlerWithVaryHeaderFixed();
        gzipHandler.setCompressionLevel(serverConfig.responseCompressionLevel());
        gzipHandler.setInflateBufferSize(8 * 1024);
        gzipHandler.setIncludedMethods("GET", "POST", "PUT", "PATCH");
        return gzipHandler;
    }

    /** A subclass which overrides Jetty's default behavior of including user-agent in the vary field */
    private static class GzipHandlerWithVaryHeaderFixed extends GzipHandler {

        @Override
        public HttpField getVaryField() {
            return GzipHttpOutputInterceptor.VARY_ACCEPT_ENCODING;
        }

    }

}
