// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.util.Modules;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.DeactivatedContainer;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Einar M R Rosenvinge
 */
public class StandaloneContainerActivator implements BundleActivator {

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        for (ConnectorConfig config: getConnectorConfigs(getContainer())) {
            ServerSocketChannel socketChannel = bindChannel(config);
            registerChannels(bundleContext, config.listenPort(), socketChannel);
        }
    }

    static void registerChannels(BundleContext bundleContext, int listenPort, ServerSocketChannel boundChannel) {
        Hashtable<String, Integer> properties = new Hashtable<>();
        properties.put("port", listenPort);
        bundleContext.registerService(ServerSocketChannel.class, boundChannel, properties);
    }

    static ServerSocketChannel bindChannel(ConnectorConfig channelInfo) throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        InetSocketAddress bindAddress = new InetSocketAddress(channelInfo.listenPort());
        serverChannel.socket().bind(bindAddress, channelInfo.acceptQueueSize());
        return serverChannel;
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception { }

    static Container getContainer(Module... modules) {
        Module activatorModule = new ActivatorModule();
        List<Module> allModules = new ArrayList<>();
        allModules.addAll(Arrays.asList(modules));
        allModules.add(activatorModule);

        StandaloneContainerApplication app = new StandaloneContainerApplication(Guice.createInjector(Modules.combine(allModules)));
        return app.container();
    }

    static List<ConnectorConfig> getConnectorConfigs(Container container) {
        return getConnectorConfigs(container.getHttpServer());
    }

    private static List<ConnectorConfig> getConnectorConfigs(JettyHttpServer jettyHttpServer) {
        if (jettyHttpServer == null)
            return Collections.emptyList();

        return jettyHttpServer.getConnectorFactories().stream().
                map(StandaloneContainerActivator::getConnectorConfig).
                collect(Collectors.toList());
        }

    private static ConnectorConfig getConnectorConfig(ConnectorFactory connectorFactory) {
        return VespaModel.getConfig(ConnectorConfig.class, connectorFactory);
    }


    private static class ActivatorModule implements Module {
        @Override
        public void configure(Binder binder) {
            binder.bind(OsgiFramework.class).toInstance(new DummyOsgiFramework());
            binder.bind(ContainerActivator.class).toInstance(new DummyActivatorForStandaloneContainerApp());
        }
    }

    private static class DummyActivatorForStandaloneContainerApp implements ContainerActivator {
        @Override
        public ContainerBuilder newContainerBuilder() {
            return new ContainerBuilder(new ArrayList<Module>());
        }

        @Override
        public DeactivatedContainer activateContainer(ContainerBuilder builder) {
            return new DeactivatedContainer() {
                @Override
                public Object appContext() {
                    return new Object();
                }

                @Override
                public void notifyTermination(Runnable task) {
                }
            };
        }
    }

    public static class DummyOsgiFramework implements OsgiFramework {
        @Override
        public List<Bundle> installBundle(String bundleLocation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void startBundles(List<Bundle> bundles, boolean privileged) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refreshPackages() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BundleContext bundleContext() {
            return null;
        }

        @Override
        public List<Bundle> bundles() {
            return Collections.emptyList();
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }
    }

}
