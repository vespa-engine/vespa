// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.container.jdisc.config.MetricDefaultsConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.yahoo.collections.CollectionUtil.first;
import static com.yahoo.container.core.AccessLogConfig.FileHandler.RotateScheme;
import static com.yahoo.vespa.model.container.xml.BundleMapper.absoluteBundlePath;

/**
 * @author tonytv
 */
public final class ManhattanContainerModelBuilder extends ContainerModelBuilder {

    static final String MANHATTAN_FILE_NAME_PATTERN = Defaults.getDefaults().vespaHome() + "logs/jdisc_core/access.%Y-%m-%d-%H";
    static final String MANHATTAN_ROTATION_INTERVAL = "0 60 ...";
    static final RotateScheme.Enum MANHATTAN_ROTATION_SCHEME = RotateScheme.DATE;
    static final String MANHATTAN_SYMLINK_NAME = "access";

    public interface BundleFiles {
        // TODO: move constants to the DH code base.
        Set<Path> dhBundles = new HashSet<>(Arrays.asList(
            Paths.get("apache_avro/avro.jar"),
            Paths.get("apache_avro/commons-compress.jar"),
            Paths.get("apache_avro/paranamer.jar"),
            Paths.get("apache_avro/jackson-core-asl.jar"),
            Paths.get("apache_avro/jackson-mapper-asl.jar"),
            Paths.get("dh_rainbow_client_api_java.jar"),
            Paths.get("dh_rainbow_util_batch_java.jar"),
            Paths.get("dh_rainbow_util_java.jar")));
    }

    private final int httpPort;
    private JettyHttpServer jettyHttpServer;

    public ManhattanContainerModelBuilder(int httpPort) {
        super(true, Networking.enable);
        this.httpPort = httpPort;
    }

    @Override
    protected void addBundlesForPlatformComponents(ContainerCluster cluster) {
        super.addBundlesForPlatformComponents(cluster);
        BundleFiles.dhBundles.forEach(
                bundleFile -> cluster.addPlatformBundle(absoluteBundlePath(bundleFile)));
    }

    @Override
    protected void setDefaultMetricConsumerFactory(ContainerCluster cluster) {
        cluster.setDefaultMetricConsumerFactory(MetricDefaultsConfig.Factory.Enum.YAMAS_SCOREBOARD);
    }

    @Override
    protected void addAccessLogs(ContainerCluster cluster, Element spec) {
        warnIfAccessLogsDefined(spec);

        checkNotNull(jettyHttpServer, "addHttp must be called first");
        cluster.addComponent(createManhattanAccessLog());
    }

    private Component createManhattanAccessLog() {
        return new AccessLogComponent(AccessLogComponent.AccessLogType.yApacheAccessLog,
                MANHATTAN_FILE_NAME_PATTERN,
                MANHATTAN_ROTATION_INTERVAL,
                MANHATTAN_ROTATION_SCHEME,
                MANHATTAN_SYMLINK_NAME);
    }

    private void warnIfAccessLogsDefined(Element spec) {
        List<Element> accessLogElements = getAccessLogElements(spec);
        if (!accessLogElements.isEmpty()) {
            logManhattanInfo("Ignoring " + accessLogElements.size() +
                    " access log elements in services.xml, using default yapache access logging instead.");
        }
    }

    @Override
    protected void addDefaultHandlers(ContainerCluster cluster) {
        addDefaultHandlersExceptStatus(cluster);
    }

    @Override
    protected void addStatusHandlers(ContainerCluster cluster, ConfigModelContext configModelContext) {
        addStatusHandlerForJDiscStatusPackage(cluster, "status.html"); //jdisc_status
        addStatusHandlerForJDiscStatusPackage(cluster, "akamai");      //jdisc_akamai
    }

    private static void addStatusHandlerForJDiscStatusPackage(ContainerCluster cluster, String name) {
        cluster.addComponent(
                new FileStatusHandlerComponent(name + "-status-handler", Defaults.getDefaults().vespaHome() + "libexec/jdisc/" + name,
                                               "http://*/" + name, "https://*/" + name));
    }

    @Override
    protected void addHttp(Element spec, ContainerCluster cluster) {
        super.addHttp(spec, cluster);
        ensureHasHttp(cluster);
        ensureOneHttpServer(cluster.getHttp());
    }

    private void ensureHasHttp(ContainerCluster cluster) {
        if (cluster.getHttp() == null)
            cluster.setHttp(createHttp());
    }

    private Http createHttp() {
        Http http = new Http(Collections.<Http.Binding>emptyList());
        http.setFilterChains(new FilterChains(http));
        return http;
    }

    private void ensureOneHttpServer(Http http) {
        if (http.getHttpServer() == null || http.getHttpServer().getConnectorFactories().isEmpty()) {
            JettyHttpServer jettyHttpServer = new JettyHttpServer(new ComponentId("main-http-server"));
            http.setHttpServer(jettyHttpServer);
            ConnectorFactory connectorFactory = new ConnectorFactory("main-http-connector",
                                                                     httpPort, null);
            http.getHttpServer().addConnector(connectorFactory);
        } else {
            removeAllButOneConnector(http.getHttpServer());
            ConnectorFactory connectorFactory = first(http.getHttpServer().getConnectorFactories());
            connectorFactory.setListenPort(httpPort);
        }
        jettyHttpServer = http.getHttpServer();
    }

    private void removeAllButOneConnector(JettyHttpServer jettyHttpServer) {
        int removed = 0;

        if (jettyHttpServer.getConnectorFactories().size() > 1) {
            for (int i = jettyHttpServer.getConnectorFactories().size() - 1; i > 0; i--) {
                ConnectorFactory c = jettyHttpServer.getConnectorFactories().get(i);
                jettyHttpServer.removeConnector(c);
                ++removed;
            }
        }

        if (removed > 0) {
            logManhattanInfo("Using only the first http server " + jettyHttpServer.getConnectorFactories().get(0).getName());
        }
    }

    private static <E> List<E> tail(List<E> list) {
        return list.subList(1, list.size());
    }

    private void logManhattanInfo(String message) {
        log.log(Level.INFO, "[Manhattan] " + message);
    }
}
