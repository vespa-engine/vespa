// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.common.util.concurrent.AtomicDouble;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.component.Vtag;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.config.subscription.SubscriberClosedException;
import com.yahoo.container.Container;
import com.yahoo.container.QrConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.config.HandlersConfigurerDi;
import com.yahoo.container.di.CloudSubscriberFactory;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.container.http.filter.FilterChainRepository;
import com.yahoo.container.jdisc.component.Deconstructor;
import com.yahoo.container.jdisc.metric.DisableGuiceMetric;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.DeactivatedContainer;
import com.yahoo.jdisc.application.GuiceRepository;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Register;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.log.impl.InitializeLog;
import com.yahoo.messagebus.network.rpc.SlobrokConfigSubscriber;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.yolean.Exceptions;
import com.yahoo.yolean.UncheckedInterruptedException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Provider;
import java.security.Security;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.collections.CollectionUtil.first;

/**
 * @author Tony Vaagenes
 */
public final class ConfiguredApplication implements Application {

    private static final Logger log = Logger.getLogger(ConfiguredApplication.class.getName());
    private final Object monitor = new Object();
    private final Set<ClientProvider> startedClients = createIdentityHashSet();
    private final Set<ServerProvider> startedServers = createIdentityHashSet();
    private final SubscriberFactory subscriberFactory;
    private final Metric metric;
    private final ContainerActivator activator;
    private final String configId;
    private final OsgiFramework osgiFramework;
    private final com.yahoo.jdisc.Timer timerSingleton;
    private final AtomicBoolean dumpHeapOnShutdownTimeout = new AtomicBoolean(false);
    private final AtomicDouble shutdownTimeoutS = new AtomicDouble(50.0);
    // Subscriber that is used when this is not a standalone-container. Subscribes
    // to config to make sure that container will be registered in slobrok (by {@link com.yahoo.jrt.slobrok.api.Register})
    // if slobrok config changes (typically slobroks moving to other nodes)
    private final Optional<SlobrokConfigSubscriber> slobrokConfigSubscriber;
    private final ShutdownDeadline shutdownDeadline;

    //TODO: FilterChainRepository should instead always be set up in the model.
    private final FilterChainRepository defaultFilterChainRepository =
            new FilterChainRepository(new ChainsConfig(new ChainsConfig.Builder()),
                                      new ComponentRegistry<>(),
                                      new ComponentRegistry<>(),
                                      new ComponentRegistry<>(),
                                      new ComponentRegistry<>());
    private final OsgiFramework restrictedOsgiFramework;
    private final Phaser nonTerminatedContainerTracker = new Phaser(1);
    private final Thread reconfigurerThread;
    private final Thread portWatcher;
    private HandlersConfigurerDi configurer;
    private QrConfig qrConfig;

    private Register slobrokRegistrator = null;
    private Supervisor supervisor = null;
    private Acceptor acceptor = null;
    private volatile boolean shutdownReconfiguration = false;

    static {
        log.log(Level.INFO, "Starting jdisc" + (Vtag.currentVersion.isEmpty() ? "" : " at version " + Vtag.currentVersion));
        installBouncyCastleSecurityProvider();
    }

    /**
     * Eagerly install BouncyCastle as security provider. It's done here to ensure no bundle is able install this security provider.
     * If a bundle install this provider and the bundle is later uninstall,
     * it will break havoc if the installed security provider tries to load new classes.
     */
    private static void installBouncyCastleSecurityProvider() {
        BouncyCastleProvider bcProvider = new BouncyCastleProvider();
        if (Security.addProvider(bcProvider) != -1) {
            log.info("Installed '" + bcProvider.getInfo() + "' as Java Security Provider");
        } else {
            Provider alreadyInstalledBcProvider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME);
            log.warning("Unable to install '" + bcProvider.getInfo() + "' as Java Security Provider. " +
                    "A provider '" + alreadyInstalledBcProvider.getInfo() + "' is already installed.");
        }
    }

    /**
     * Do not delete this method even if it's empty.
     * Calling this methods forces this class to be loaded,
     * which runs the static block.
     */
    @SuppressWarnings("UnusedDeclaration")
    public static void ensureVespaLoggingInitialized() {

    }

    @Inject
    public ConfiguredApplication(ContainerActivator activator,
                                 OsgiFramework osgiFramework,
                                 com.yahoo.jdisc.Timer timer,
                                 SubscriberFactory subscriberFactory,
                                 Metric metric) {
        this.activator = activator;
        this.osgiFramework = osgiFramework;
        this.timerSingleton = timer;
        this.subscriberFactory = subscriberFactory;
        this.metric = metric;
        this.configId = System.getProperty("config.id");
        this.slobrokConfigSubscriber = (subscriberFactory instanceof CloudSubscriberFactory)
                ? Optional.of(new SlobrokConfigSubscriber(configId))
                : Optional.empty();
        this.restrictedOsgiFramework = new DisableOsgiFramework(new RestrictedBundleContext(osgiFramework.bundleContext()));
        this.shutdownDeadline = new ShutdownDeadline(configId);
        this.reconfigurerThread = new Thread(this::doReconfigurationLoop, "configured-application-reconfigurer");
        this.portWatcher = new Thread(this::watchPortChange, "configured-application-port-watcher");
    }

    @Override
    public void start() {
        qrConfig = getConfig(QrConfig.class, true);
        reconfigure(qrConfig);
        hackToInitializeServer(qrConfig);

        ContainerBuilder builder = createBuilderWithGuiceBindings();
        configurer = createConfigurer(builder.guiceModules().activate());
        initializeAndActivateContainer(builder, () -> {});
        reconfigurerThread.setDaemon(true);
        reconfigurerThread.start();

        portWatcher.setDaemon(true);
        portWatcher.start();
        if (setupRpc()) {
            slobrokRegistrator = registerInSlobrok(qrConfig); // marks this as up
        }
    }

    private boolean setupRpc() {
        if ( ! qrConfig.rpc().enabled()) return false;
        supervisor = new Supervisor(new Transport("configured-application")).setDropEmptyBuffers(true);
        supervisor.addMethod(new Method("prepareStop", "d", "", this::prepareStop));
        Spec listenSpec = new Spec(qrConfig.rpc().port());
        try {
            acceptor = supervisor.listen(listenSpec);
            return true;
        } catch (ListenFailedException e) {
            throw new RuntimeException("Could not create rpc server listening on " + listenSpec, e);
        }
    }

    private Register registerInSlobrok(QrConfig qrConfig) {
        SlobrokList slobrokList = getSlobrokList();
        Spec mySpec = new Spec(HostName.getLocalhost(), acceptor.port());
        Register slobrokRegistrator = new Register(supervisor, slobrokList, mySpec);
        slobrokRegistrator.registerName(qrConfig.rpc().slobrokId());
        log.log(Level.INFO, "Registered name '" + qrConfig.rpc().slobrokId() +
                               "' at " + mySpec + " with: " + slobrokList);
        return slobrokRegistrator;
    }

    // Different ways of getting slobrok config depending on whether we have a subscriber (regular setup)
    // or need to get the config directly (standalone container)
    private SlobrokList getSlobrokList() {
        SlobrokList slobrokList;
        if (slobrokConfigSubscriber.isPresent()) {
            slobrokList = slobrokConfigSubscriber.get().getSlobroks();
        } else {
            slobrokList = new SlobrokList();
            SlobroksConfig slobrokConfig = getConfig(SlobroksConfig.class, true);
            slobrokList.setup(slobrokConfig.slobrok().stream().map(SlobroksConfig.Slobrok::connectionspec).toArray(String[]::new));
        }
        return slobrokList;
    }

    private void unregisterInSlobrok() {
        if (slobrokRegistrator != null)
            slobrokRegistrator.shutdown();
        if (acceptor != null)
            acceptor.shutdown().join();
        if (supervisor != null)
            supervisor.transport().shutdown().join();
    }

    private static void hackToInitializeServer(QrConfig config) {
        try {
            Container.get().setupFileAcquirer(config.filedistributor());
            Container.get().setupUrlDownloader();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Caught exception when initializing server. Exiting.", e);
            Runtime.getRuntime().halt(1);
        }
    }

    private <T extends ConfigInstance> T getConfig(Class<T> configClass, boolean isInitializing) {
        Subscriber subscriber = subscriberFactory.getSubscriber(Collections.singleton(new ConfigKey<>(configClass, configId)),
                                                                configClass.getName());
        try {
            subscriber.waitNextGeneration(isInitializing);
            return configClass.cast(first(subscriber.config().values()));
        } finally {
            subscriber.close();
        }
    }

    private void watchPortChange() {
        Subscriber subscriber = subscriberFactory.getSubscriber(Collections.singleton(new ConfigKey<>(QrConfig.class, configId)),
                                                                "portWatcher");
        try {
            while (true) {
                subscriber.waitNextGeneration(false);
                QrConfig newConfig = QrConfig.class.cast(first(subscriber.config().values()));
                reconfigure(qrConfig);
                if (qrConfig.rpc().port() != newConfig.rpc().port()) {
                    com.yahoo.protect.Process.logAndDie(
                            "Rpc port config has changed from " +
                            qrConfig.rpc().port() + " to " + newConfig.rpc().port() +
                            ". This we can not handle without a restart so we will just bail out.");
                }
                log.fine("Received new QrConfig :" + newConfig);
            }
        } finally {
            subscriber.close();
        }
    }

    void reconfigure(QrConfig qrConfig) {
        dumpHeapOnShutdownTimeout.set(qrConfig.shutdown().dumpHeapOnTimeout());
        shutdownTimeoutS.set(qrConfig.shutdown().timeout());
    }

    private void initializeAndActivateContainer(ContainerBuilder builder, Runnable cleanupTask) {
        addHandlerBindings(builder, Container.get().getRequestHandlerRegistry(),
                           configurer.getComponent(ApplicationContext.class).discBindingsConfig);
        List<ServerProvider> currentServers = Container.get().getServerProviderRegistry().allComponents();
        for (ServerProvider server : currentServers) {
            builder.serverProviders().install(server);
        }
        activateContainer(builder, cleanupTask);
        startAndStopServers(currentServers);

        startAndRemoveClients(Container.get().getClientProviderRegistry().allComponents());

        log.info("Switching to the latest deployed set of configurations and components. " +
                 "Application config generation: " + configurer.generation());
        metric.set("application_generation", configurer.generation(), metric.createContext(Map.of()));
    }

    private void activateContainer(ContainerBuilder builder, Runnable onPreviousContainerTermination) {
        DeactivatedContainer deactivated = activator.activateContainer(builder);
        if (deactivated != null) {
            nonTerminatedContainerTracker.register();
            deactivated.notifyTermination(() -> {
                try {
                    onPreviousContainerTermination.run();
                } finally {
                    nonTerminatedContainerTracker.arriveAndDeregister();
                }
            });
        }
    }

    private ContainerBuilder createBuilderWithGuiceBindings() {
        ContainerBuilder builder = activator.newContainerBuilder();
        setupGuiceBindings(builder.guiceModules());
        return builder;
    }

    @SuppressWarnings("removal") // TODO Vespa 8: remove
    private void doReconfigurationLoop() {
        while (!shutdownReconfiguration) {
            try {
                ContainerBuilder builder = createBuilderWithGuiceBindings();

                // Block until new config arrives, and it should be applied
                Runnable cleanupTask = configurer.waitForNextGraphGeneration(builder.guiceModules().activate(), false);
                initializeAndActivateContainer(builder, cleanupTask);
            } catch (UncheckedInterruptedException | SubscriberClosedException | ConfigInterruptedException e) {
                break;
            } catch (Exception | LinkageError e) { // LinkageError: OSGi problems
                tryReportFailedComponentGraphConstructionMetric(configurer, e);
                log.log(Level.SEVERE,
                        "Reconfiguration failed, your application package must be fixed, unless this is a " +
                                "JNI reload issue: " + Exceptions.toMessageString(e), e);
            } catch (Error e) {
                com.yahoo.protect.Process.logAndDie("java.lang.Error on reconfiguration: We are probably in " +
                        "a bad state and will terminate", e);
            }
        }
        log.fine("Reconfiguration loop exited");
    }

    private static void tryReportFailedComponentGraphConstructionMetric(HandlersConfigurerDi configurer, Throwable error) {
        try {
            // We need the Metric instance from previous component graph to report metric values
            // Metric may not be available if this is the initial component graph (since metric wiring is done through the config model)
            Metric metric = configurer.getComponent(Metric.class);
            Metric.Context metricContext = metric.createContext(Map.of("exception", error.getClass().getSimpleName()));
            metric.add("jdisc.application.failed_component_graphs", 1L, metricContext);
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to report metric for failed component graph: " + e.getMessage(), e);
        }
    }

    private void startAndStopServers(List<ServerProvider> currentServers) {
        synchronized (monitor) {
            Set<ServerProvider> serversToClose = createIdentityHashSet(startedServers);
            serversToClose.removeAll(currentServers);
            if (serversToClose.size() > 0) {
                log.info(String.format("Closing %d server instances", serversToClose.size()));
                for (ServerProvider server : serversToClose) {
                    server.close();
                    startedServers.remove(server);
                }
            }
            for (ServerProvider server : currentServers) {
                if (!startedServers.contains(server)) {
                    server.start();
                    startedServers.add(server);
                }
            }
        }
    }

    private void startAndRemoveClients(List<ClientProvider> currentClients) {
        synchronized (monitor) {
            Set<ClientProvider> clientToRemove = createIdentityHashSet(startedClients);
            clientToRemove.removeAll(currentClients);
            for (ClientProvider client : clientToRemove) {
                startedClients.remove(client);
            }
            for (ClientProvider client : currentClients) {
                if (!startedClients.contains(client)) {
                    client.start();
                    startedClients.add(client);
                }
            }
        }
    }

    private HandlersConfigurerDi createConfigurer(Injector discInjector) {
        return new HandlersConfigurerDi(subscriberFactory,
                                        Container.get(),
                                        configId,
                                        new Deconstructor(),
                                        discInjector,
                                        osgiFramework);
    }

    private void setupGuiceBindings(GuiceRepository modules) {
        modules.install(new AbstractModule() {

            @Override
            protected void configure() {
                bind(Metric.class).to(DisableGuiceMetric.class);
                bind(OsgiFramework.class).toInstance(restrictedOsgiFramework);
                bind(com.yahoo.jdisc.Timer.class).toInstance(timerSingleton);
                bind(FilterChainRepository.class).toInstance(defaultFilterChainRepository);
            }
        });
    }

    @Override
    public void stop() {
        log.info("Stop: Initiated");
        shutdownDeadline.schedule((long)(shutdownTimeoutS.get() * 1000), dumpHeapOnShutdownTimeout.get());
        stopServersAndAwaitTermination();
        log.info("Stop: Finished");
    }

    private void prepareStop(Request request) {
        log.info("PrepareStop: Initiated");
        long timeoutMillis = (long) (request.parameters().get(0).asDouble() * 1000);
        try (ShutdownDeadline ignored =
                     new ShutdownDeadline(configId).schedule(timeoutMillis, dumpHeapOnShutdownTimeout.get())) {
            stopServersAndAwaitTermination();
            log.info("PrepareStop: Finished");
        } catch (Exception e) {
            request.setError(ErrorCode.METHOD_FAILED, e.getMessage());
            throw e;
        }
    }

    private void stopServersAndAwaitTermination() {
        shutdownReconfigurer();
        startAndStopServers(List.of());
        startAndRemoveClients(List.of());
        activateContainer(null, () -> log.info("Last active container generation has terminated"));
        nonTerminatedContainerTracker.arriveAndAwaitAdvance();
    }

    private void shutdownReconfigurer() {
        if (!reconfigurerThread.isAlive()) return;
        log.info("Shutting down reconfiguration thread");
        long start = System.currentTimeMillis();
        shutdownReconfiguration = true;
        configurer.shutdownConfigRetriever();
        try {
            reconfigurerThread.join();
            log.info(String.format(
                    "Reconfiguration thread shutdown completed in %.3f seconds", (System.currentTimeMillis() - start) / 1000D));
        } catch (InterruptedException e) {
            String message = "Interrupted while waiting for reconfiguration shutdown";
            log.warning(message);
            log.log(Level.FINE, e.getMessage(), e);
            throw new UncheckedInterruptedException(message, true);
        }
    }

    @Override
    public void destroy() {
        log.info("Destroy: Shutting down container now");
        if (configurer != null) {
            configurer.shutdown();
        }
        slobrokConfigSubscriber.ifPresent(SlobrokConfigSubscriber::shutdown);
        Container.get().shutdown();
        unregisterInSlobrok();
        shutdownDeadline.cancel();
        log.info("Destroy: Finished");
    }

    private static void addHandlerBindings(ContainerBuilder builder,
                                           ComponentRegistry<RequestHandler> requestHandlerRegistry,
                                           JdiscBindingsConfig discBindingsConfig) {
        for (Map.Entry<String, JdiscBindingsConfig.Handlers> handlerEntry : discBindingsConfig.handlers().entrySet()) {
            String id = handlerEntry.getKey();
            JdiscBindingsConfig.Handlers handlerConfig = handlerEntry.getValue();

            RequestHandler handler = requestHandlerRegistry.getComponent(id);
            if (handler == null) {
                throw new RuntimeException("Binding configured for non-jdisc request handler " + id);
            }
            bindUri(builder.serverBindings(), handlerConfig.serverBindings(), handler);
            bindUri(builder.clientBindings(), handlerConfig.clientBindings(), handler);
        }
    }

    private static void bindUri(BindingRepository<RequestHandler> bindings, List<String> uriPatterns,
                                RequestHandler target) {
        for (String uri : uriPatterns) {
            bindings.bind(uri, target);
        }
    }

    private static <E> Set<E> createIdentityHashSet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    private static <E> Set<E> createIdentityHashSet(Collection<E> items) {
        Set<E> set = createIdentityHashSet();
        set.addAll(items);
        return set;
    }

    public static final class ApplicationContext {

        final JdiscBindingsConfig discBindingsConfig;

        public ApplicationContext(com.yahoo.container.jdisc.JdiscBindingsConfig discBindingsConfig) {
            this.discBindingsConfig = discBindingsConfig;
        }
    }

}
