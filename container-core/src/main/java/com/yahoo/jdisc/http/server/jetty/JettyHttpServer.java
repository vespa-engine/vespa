// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Inject;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
import com.yahoo.jdisc.service.AbstractServerProvider;
import com.yahoo.jdisc.service.CurrentContainer;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
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

import javax.management.remote.JMXServiceURL;
import javax.servlet.DispatcherType;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
public class JettyHttpServer extends AbstractServerProvider {

    private final static Logger log = Logger.getLogger(JettyHttpServer.class.getName());

    private final ExecutorService janitor;

    private final Server server;
    private final List<Integer> listenedPorts = new ArrayList<>();
    private final ServerMetricReporter metricsReporter;

    @Inject
    public JettyHttpServer(CurrentContainer container,
                           Metric metric,
                           ServerConfig serverConfig,
                           ServletPathsConfig servletPathsConfig,
                           FilterBindings filterBindings,
                           ComponentRegistry<ConnectorFactory> connectorFactories,
                           ComponentRegistry<ServletHolder> servletHolders,
                           FilterInvoker filterInvoker,
                           RequestLog requestLog,
                           ConnectionLog connectionLog) {
        super(container);
        if (connectorFactories.allComponents().isEmpty())
            throw new IllegalArgumentException("No connectors configured.");

        initializeJettyLogging();

        server = new Server();
        server.setStopTimeout((long)(serverConfig.stopTimeout() * 1000.0));
        server.setRequestLog(new AccessLogRequestLog(requestLog, serverConfig.accessLog()));
        setupJmx(server, serverConfig);
        configureJettyThreadpool(server, serverConfig);
        JettyConnectionLogger connectionLogger = new JettyConnectionLogger(serverConfig.connectionLog(), connectionLog);

        for (ConnectorFactory connectorFactory : connectorFactories.allComponents()) {
            ConnectorConfig connectorConfig = connectorFactory.getConnectorConfig();
            server.addConnector(connectorFactory.createConnector(metric, server, connectionLogger));
            listenedPorts.add(connectorConfig.listenPort());
        }

        janitor = newJanitor();

        JDiscContext jDiscContext = new JDiscContext(filterBindings,
                                                     container,
                                                     janitor,
                                                     metric,
                                                     serverConfig);

        ServletHolder jdiscServlet = new ServletHolder(new JDiscHttpServlet(jDiscContext));
        FilterHolder jDiscFilterInvokerFilter = new FilterHolder(new JDiscFilterInvokerFilter(jDiscContext, filterInvoker));

        List<JDiscServerConnector> connectors = Arrays.stream(server.getConnectors())
                                                      .map(JDiscServerConnector.class::cast)
                                                      .collect(toList());

        server.setHandler(getHandlerCollection(serverConfig,
                                               servletPathsConfig,
                                               connectors,
                                               jdiscServlet,
                                               servletHolders,
                                               jDiscFilterInvokerFilter));
        this.metricsReporter = new ServerMetricReporter(metric, server);
    }

    private static void initializeJettyLogging() {
        // Note: Jetty is logging stderr if no logger is explicitly configured
        try {
            Log.setLog(new JavaUtilLog());
        } catch (Exception e) {
            throw new RuntimeException("Unable to initialize logging framework for Jetty");
        }
    }

    private static void setupJmx(Server server, ServerConfig serverConfig) {
        if (serverConfig.jmx().enabled()) {
            System.setProperty("java.rmi.server.hostname", "localhost");
            server.addBean(new MBeanContainer(ManagementFactory.getPlatformMBeanServer()));
            server.addBean(new ConnectorServer(createJmxLoopbackOnlyServiceUrl(serverConfig.jmx().listenPort()),
                                               "org.eclipse.jetty.jmx:name=rmiconnectorserver"));
        }
    }

    private static void configureJettyThreadpool(Server server, ServerConfig config) {
        QueuedThreadPool pool = (QueuedThreadPool) server.getThreadPool();
        pool.setMaxThreads(config.maxWorkerThreads());
        pool.setMinThreads(config.minWorkerThreads());
    }

    private static JMXServiceURL createJmxLoopbackOnlyServiceUrl(int port) {
        try {
            return new JMXServiceURL("rmi", "localhost", port, "/jndi/rmi://localhost:" + port + "/jmxrmi");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private HandlerCollection getHandlerCollection(ServerConfig serverConfig,
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

        List<ConnectorConfig> connectorConfigs = connectors.stream().map(JDiscServerConnector::connectorConfig).collect(toList());
        var secureRedirectHandler = new SecuredRedirectHandler(connectorConfigs);
        secureRedirectHandler.setHandler(servletContextHandler);

        var proxyHandler = new HealthCheckProxyHandler(connectors);
        proxyHandler.setHandler(secureRedirectHandler);

        var authEnforcer = new TlsClientAuthenticationEnforcer(connectorConfigs);
        authEnforcer.setHandler(proxyHandler);

        GzipHandler gzipHandler = newGzipHandler(serverConfig);
        gzipHandler.setHandler(authEnforcer);

        HttpResponseStatisticsCollector statisticsCollector =
                new HttpResponseStatisticsCollector(serverConfig.metric().monitoringHandlerPaths(),
                                                    serverConfig.metric().searchHandlerPaths());
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

    // Separate threadpool for tasks that cannot be executed on the jdisc default threadpool due to risk of deadlock
    private static ExecutorService newJanitor() {
        int threadPoolSize = Math.max(1, Runtime.getRuntime().availableProcessors()/8);
        log.info("Creating janitor executor with " + threadPoolSize + " threads");
        return Executors.newFixedThreadPool(
                threadPoolSize,
                new DaemonThreadFactory(JettyHttpServer.class.getName() + "-Janitor-"));
    }

    @Override
    public void start() {
        try {
            server.start();
            metricsReporter.start();
            logEffectiveSslConfiguration();
        } catch (final Exception e) {
            if (e instanceof IOException && e.getCause() instanceof BindException) {
                throw new RuntimeException("Failed to start server due to BindException. ListenPorts = " + listenedPorts.toString(), e.getCause());
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

        metricsReporter.shutdown();
        janitor.shutdown();
    }

    private boolean isGracefulShutdownEnabled() {
        return server.getChildHandlersByClass(StatisticsHandler.class).length > 0 && server.getStopTimeout() > 0;
    }

    public int getListenPort() {
        return ((ServerConnector)server.getConnectors()[0]).getLocalPort();
    }

    Server server() { return server; }

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
