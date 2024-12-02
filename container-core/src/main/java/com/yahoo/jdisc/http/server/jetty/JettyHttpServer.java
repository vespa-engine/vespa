// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.inject.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.RequestLog;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ServerConfig;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.service.ServerProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.jmx.ConnectorServer;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.server.handler.gzip.GzipHttpOutputInterceptor;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.BindException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author Simon Thoresen Hult
 * @author bjorncs
 */
public class JettyHttpServer extends AbstractResource implements ServerProvider {

    private final static Logger log = Logger.getLogger(JettyHttpServer.class.getName());

    private final ServerConfig config;
    private final Server server;
    private final List<Integer> listenedPorts = new ArrayList<>();
    private final ServerMetricReporter metricsReporter;
    private final Deque<JDiscContext> contexts = new ConcurrentLinkedDeque<>();

    @Inject // ServerProvider implementors must use com.google.inject.Inject
    public JettyHttpServer(Metric metric,
                           ServerConfig serverConfig,
                           ComponentRegistry<ConnectorFactory> connectorFactories,
                           RequestLog requestLog,
                           ConnectionLog connectionLog) {
        if (connectorFactories.allComponents().isEmpty())
            throw new IllegalArgumentException("No connectors configured.");

        this.config = serverConfig;
        server = new Server();
        server.setStopTimeout((long)(serverConfig.stopTimeout() * 1000.0));
        server.setRequestLog(new AccessLogRequestLog(requestLog));
        setupJmx(server, serverConfig);
        configureJettyThreadpool(server, serverConfig);
        JettyConnectionLogger connectionLogger = new JettyConnectionLogger(serverConfig.connectionLog(), connectionLog);
        ConnectionMetricAggregator connectionMetricAggregator = new ConnectionMetricAggregator(serverConfig, metric);

        for (ConnectorFactory connectorFactory : connectorFactories.allComponents()) {
            ConnectorConfig connectorConfig = connectorFactory.getConnectorConfig();
            server.addConnector(connectorFactory.createConnector(metric, server, connectionLogger, connectionMetricAggregator));
            listenedPorts.add(connectorConfig.listenPort());
        }
        server.addBeanToAllConnectors(new ResponseMetricAggregator(serverConfig.metric()));

        ServletHolder jdiscServlet = new ServletHolder(new JDiscHttpServlet(this::newestContext));
        List<JDiscServerConnector> connectors = Arrays.stream(server.getConnectors())
                                                      .map(JDiscServerConnector.class::cast)
                                                      .toList();
        server.setHandler(createRootHandler(connectors, jdiscServlet));
        this.metricsReporter = new ServerMetricReporter(metric, server);
    }

    JDiscContext registerContext(FilterBindings filterBindings, CurrentContainer container, Janitor janitor, Metric metric) {
        JDiscContext context = JDiscContext.of(filterBindings, container, janitor, metric, config);
        contexts.addFirst(context);
        return context;
    }

    void deregisterContext(JDiscContext context) {
        contexts.remove(context);
    }

    JDiscContext newestContext() {
        JDiscContext context = contexts.peekFirst();
        if (context == null) throw new IllegalStateException("JettyHttpServer has no registered JDiscContext");
        return context;
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
        int cpus = Runtime.getRuntime().availableProcessors();
        QueuedThreadPool pool = (QueuedThreadPool) server.getThreadPool();
        int maxThreads = config.maxWorkerThreads() > 0 ? config.maxWorkerThreads() : 16 + cpus;
        pool.setMaxThreads(maxThreads);
        int minThreads = config.minWorkerThreads() >= 0 ? config.minWorkerThreads() : 16 + cpus;
        pool.setMinThreads(minThreads);
        log.info(String.format("Threadpool size: min=%d, max=%d", minThreads, maxThreads));
    }

    private static JMXServiceURL createJmxLoopbackOnlyServiceUrl(int port) {
        try {
            return new JMXServiceURL("rmi", "localhost", port, "/jndi/rmi://localhost:" + port + "/jmxrmi");
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private Handler createRootHandler(List<JDiscServerConnector> connectors, ServletHolder jdiscServlet) {
        HandlerCollection perConnectorHandlers = new ContextHandlerCollection();
        for (JDiscServerConnector connector : connectors) {
            ConnectorConfig connectorCfg = connector.connectorConfig();
            List<Handler> connectorChain = new ArrayList<>();
            if (connectorCfg.tlsClientAuthEnforcer().enable()) {
                connectorChain.add(newTlsClientAuthEnforcerHandler(connectorCfg));
            }
            if (connectorCfg.healthCheckProxy().enable()) {
                connectorChain.add(newHealthCheckProxyHandler(connectors));
            } else {
                connectorChain.add(newServletHandler(jdiscServlet));
            }
            ContextHandler connectorRoot = newConnectorContextHandler(connector);
            addChainToRoot(connectorRoot, connectorChain);
            perConnectorHandlers.addHandler(connectorRoot);
        }
        StatisticsHandler root = newGenericStatisticsHandler();
        addChainToRoot(root, List.of(newGzipHandler(), perConnectorHandlers));
        return root;
    }

    private static void addChainToRoot(Handler root, List<Handler> chain) {
        Handler parent = root;
        for (Handler h : chain) {
            ((HandlerWrapper)parent).setHandler(h);
            parent = h;
        }
    }

    private static String getDisplayName(List<Integer> ports) {
        return ports.stream().map(Object::toString).collect(Collectors.joining(":"));
    }

    @Override
    public void start() {
        try {
            server.start();
            if (config.metric().reporterEnabled()) metricsReporter.start();
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
                String protocols = Arrays.toString(sslContextFactory.getSelectedProtocols());
                String cipherSuites = Arrays.toString(sslContextFactory.getSelectedCipherSuites());
                log.info(String.format("TLS for port '%d': %s with %s", localPort, protocols, cipherSuites));
            }
        }
    }

    @Override
    public void close() {
        try {
            log.log(Level.INFO, String.format("Shutting down Jetty server (graceful=%b, timeout=%.1fs)",
                    isGracefulShutdownEnabled(), server.getStopTimeout()/1000d));
            long start = System.currentTimeMillis();
            server.stop();
            log.log(Level.INFO, String.format("Jetty server shutdown completed in %.3f seconds",
                    (System.currentTimeMillis()-start)/1000D));
        } catch (final Exception e) {
            log.log(Level.SEVERE, "Jetty server shutdown threw an unexpected exception.", e);
        }

        metricsReporter.shutdown();
    }

    private boolean isGracefulShutdownEnabled() {
        return server.getChildHandlersByClass(StatisticsHandler.class).length > 0 && server.getStopTimeout() > 0;
    }

    public int getListenPort() {
        return ((ServerConnector)server.getConnectors()[0]).getLocalPort();
    }

    Server server() { return server; }

    private ServletContextHandler newServletHandler(ServletHolder servlet) {
        var h = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        h.setContextPath("/");
        h.setDisplayName(getDisplayName(listenedPorts));
        h.addServlet(servlet, "/*");
        return h;
    }

    private static ContextHandler newConnectorContextHandler(JDiscServerConnector c) {
        return new ConnectorSpecificContextHandler(c);
    }

    private static HealthCheckProxyHandler newHealthCheckProxyHandler(List<JDiscServerConnector> connectors) {
        return new HealthCheckProxyHandler(connectors);
    }

    private static TlsClientAuthenticationEnforcer newTlsClientAuthEnforcerHandler(ConnectorConfig cfg) {
        return new TlsClientAuthenticationEnforcer(cfg.tlsClientAuthEnforcer());
    }

    private static StatisticsHandler newGenericStatisticsHandler() {
        StatisticsHandler statisticsHandler = new StatisticsHandler();
        statisticsHandler.statsReset();
        return statisticsHandler;
    }

    private static GzipHandler newGzipHandler() { return new GzipHandlerWithVaryHeaderFixed(); }

    /** A subclass which overrides Jetty's default behavior of including user-agent in the vary field */
    private static class GzipHandlerWithVaryHeaderFixed extends GzipHandler {

        GzipHandlerWithVaryHeaderFixed() {
            setInflateBufferSize(8 * 1024);
            setIncludedMethods("GET", "POST", "PUT", "PATCH");
        }

        @Override public HttpField getVaryField() { return GzipHttpOutputInterceptor.VARY_ACCEPT_ENCODING; }

    }

}
