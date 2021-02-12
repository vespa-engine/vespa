// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.container.Container;
import com.yahoo.container.QrConfig;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.config.HandlersConfigurerDi;
import com.yahoo.container.di.CloudSubscriberFactory;
import com.yahoo.container.di.config.Subscriber;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.container.http.filter.FilterChainRepository;
import com.yahoo.container.jdisc.component.Deconstructor;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.container.jdisc.metric.DisableGuiceMetric;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.application.Application;
import com.yahoo.jdisc.application.BindingRepository;
import com.yahoo.jdisc.application.ContainerActivator;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.application.GuiceRepository;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.jrt.slobrok.api.Register;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.log.LogSetup;
import com.yahoo.messagebus.network.rpc.SlobrokConfigSubscriber;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.yolean.Exceptions;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.collections.CollectionUtil.first;

/**
 * @author Tony Vaagenes
 */
public final class ConfiguredApplication implements Application {

    private static final Logger log = Logger.getLogger(ConfiguredApplication.class.getName());
    private static final Set<ClientProvider> startedClients = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Set<ServerProvider> startedServers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final SubscriberFactory subscriberFactory;
    private final Metric metric;
    private final ContainerActivator activator;
    private final String configId;
    private final OsgiFramework osgiFramework;
    private final com.yahoo.jdisc.Timer timerSingleton;
    // Subscriber that is used when this is not a standalone-container. Subscribes
    // to config to make sure that container will be registered in slobrok (by {@link com.yahoo.jrt.slobrok.api.Register})
    // if slobrok config changes (typically slobroks moving to other nodes)
    private final Optional<SlobrokConfigSubscriber> slobrokConfigSubscriber;
    private final SessionCache sessionCache;

    //TODO: FilterChainRepository should instead always be set up in the model.
    private final FilterChainRepository defaultFilterChainRepository =
            new FilterChainRepository(new ChainsConfig(new ChainsConfig.Builder()),
                                      new ComponentRegistry<>(),
                                      new ComponentRegistry<>(),
                                      new ComponentRegistry<>(),
                                      new ComponentRegistry<>());
    private final OsgiFramework restrictedOsgiFramework;
    private HandlersConfigurerDi configurer;
    private ScheduledThreadPoolExecutor shutdownDeadlineExecutor;
    private Thread reconfigurerThread;
    private Thread portWatcher;
    private QrConfig qrConfig;

    private Register slobrokRegistrator = null;
    private Supervisor supervisor = null;
    private Acceptor acceptor = null;

    static {
        LogSetup.initVespaLogging("Container");
        log.log(Level.INFO, "Starting container");
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
        this.sessionCache = new SessionCache(configId);
        this.restrictedOsgiFramework = new DisableOsgiFramework(new RestrictedBundleContext(osgiFramework.bundleContext()));
    }

    @Override
    public void start() {
        qrConfig = getConfig(QrConfig.class, true);

        hackToInitializeServer(qrConfig);

        ContainerBuilder builder = createBuilderWithGuiceBindings();
        configurer = createConfigurer(builder.guiceModules().activate());
        initializeAndActivateContainer(builder);
        startReconfigurerThread();
        portWatcher = new Thread(this::watchPortChange);
        portWatcher.setDaemon(true);
        portWatcher.start();
        slobrokRegistrator = registerInSlobrok(qrConfig); // marks this as up
    }

    /**
     * The container has no RPC methods, but we still need an RPC sever
     * to register in Slobrok to enable orchestration
     */
    private Register registerInSlobrok(QrConfig qrConfig) {
        if ( ! qrConfig.rpc().enabled()) return null;

        // 1. Set up RPC server
        supervisor = new Supervisor(new Transport("slobrok")).useSmallBuffers();
        Spec listenSpec = new Spec(qrConfig.rpc().port());
        try {
            acceptor = supervisor.listen(listenSpec);
        }
        catch (ListenFailedException e) {
            throw new RuntimeException("Could not create rpc server listening on " + listenSpec, e);
        }

        // 2. Register it in slobrok
        SlobrokList slobrokList = getSlobrokList();
        Spec mySpec = new Spec(HostName.getLocalhost(), acceptor.port());
        slobrokRegistrator = new Register(supervisor, slobrokList, mySpec);
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
        Subscriber subscriber = subscriberFactory.getSubscriber(Collections.singleton(new ConfigKey<>(configClass, configId)));
        try {
            subscriber.waitNextGeneration(isInitializing);
            return configClass.cast(first(subscriber.config().values()));
        } finally {
            subscriber.close();
        }
    }

    private void watchPortChange() {
        Subscriber subscriber = subscriberFactory.getSubscriber(Collections.singleton(new ConfigKey<>(QrConfig.class, configId)));
        try {
            while (true) {
                subscriber.waitNextGeneration(false);
                QrConfig newConfig = QrConfig.class.cast(first(subscriber.config().values()));
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

    private void initializeAndActivateContainer(ContainerBuilder builder) {
        addHandlerBindings(builder, Container.get().getRequestHandlerRegistry(),
                           configurer.getComponent(ApplicationContext.class).discBindingsConfig);
        installServerProviders(builder);

        activator.activateContainer(builder); // TODO: .notifyTermination(.. decompose previous component graph ..)

        startClients();
        startAndStopServers();

        log.info("Switching to the latest deployed set of configurations and components. " +
                 "Application config generation: " + configurer.generation());
        metric.set("application_generation", configurer.generation(), metric.createContext(Map.of()));
    }

    private ContainerBuilder createBuilderWithGuiceBindings() {
        ContainerBuilder builder = activator.newContainerBuilder();
        setupGuiceBindings(builder.guiceModules());
        return builder;
    }

    private void startReconfigurerThread() {
        reconfigurerThread = new Thread(() -> {
            while ( ! Thread.interrupted()) {
                try {
                    ContainerBuilder builder = createBuilderWithGuiceBindings();

                    // Block until new config arrives, and it should be applied
                    configurer.getNewComponentGraph(builder.guiceModules().activate(), false);
                    initializeAndActivateContainer(builder);
                } catch (ConfigInterruptedException e) {
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
            log.fine("Shutting down HandlersConfigurerDi");
        });
        reconfigurerThread.start();
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

    private static void installServerProviders(ContainerBuilder builder) {
        List<ServerProvider> serverProviders = Container.get().getServerProviderRegistry().allComponents();
        for (ServerProvider server : serverProviders) {
            builder.serverProviders().install(server);
        }
    }

    private static void startClients() {
        for (ClientProvider client : Container.get().getClientProviderRegistry().allComponents()) {
            if (!startedClients.contains(client)) {
                client.start();
                startedClients.add(client);
            }
        }
    }

    private static void startAndStopServers() {
        List<ServerProvider> currentServers = Container.get().getServerProviderRegistry().allComponents();
        HashSet<ServerProvider> serversToClose = new HashSet<>(startedServers);
        serversToClose.removeAll(currentServers);
        for (ServerProvider server : serversToClose) {
            closeServer(server);
        }
        for (ServerProvider server : currentServers) {
            if (!startedServers.contains(server)) {
                server.start();
                startedServers.add(server);
            }
        }
    }

    private static void closeServer(ServerProvider server) {
        server.close();
        startedServers.remove(server);
    }

    private HandlersConfigurerDi createConfigurer(Injector discInjector) {
        return new HandlersConfigurerDi(subscriberFactory,
                                        Container.get(),
                                        configId,
                                        new Deconstructor(Deconstructor.Mode.RECONFIG),
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
                bind(SessionCache.class).toInstance(sessionCache); // Needed by e.g. FeedHandler
            }
        });
    }

    @Override
    public void stop() {
        startShutdownDeadlineExecutor();
        shutdownReconfigurerThread();

        log.info("Stop: Closing servers");
        for (ServerProvider server : Container.get().getServerProviderRegistry().allComponents()) {
            if (startedServers.contains(server)) {
                closeServer(server);
            }
        }

        log.info("Stop: Shutting container down");
        configurer.shutdown(new Deconstructor(Deconstructor.Mode.SHUTDOWN));
        slobrokConfigSubscriber.ifPresent(SlobrokConfigSubscriber::shutdown);
        Container.get().shutdown();

        unregisterInSlobrok();
        LogSetup.cleanup();
        log.info("Stop: Finished");
    }

    private void shutdownReconfigurerThread() {
        if (reconfigurerThread == null) return;
        reconfigurerThread.interrupt();
        try {
            //Workaround for component constructors masking InterruptedException.
            while (reconfigurerThread.isAlive()) {
                reconfigurerThread.interrupt();
                long millis = 200;
                reconfigurerThread.join(millis);
            }
        } catch (InterruptedException e) {
            log.info("Interrupted while joining on HandlersConfigurer reconfigure thread.");
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        if (shutdownDeadlineExecutor != null) { //stop() is not called when exception happens during start
            shutdownDeadlineExecutor.shutdownNow();
        }
    }

    // Workaround for ApplicationLoader.stop not being able to shutdown
    private void startShutdownDeadlineExecutor() {
        shutdownDeadlineExecutor = new ScheduledThreadPoolExecutor(1, new DaemonThreadFactory("Shutdown deadline timer"));
        shutdownDeadlineExecutor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        long delayMillis = 50 * 1000;
        shutdownDeadlineExecutor.schedule(() -> com.yahoo.protect.Process.logAndDie(
                "Timed out waiting for application shutdown. Please check that all your request handlers " +
                        "drain their request content channels.", true), delayMillis, TimeUnit.MILLISECONDS);
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

    public static final class ApplicationContext {

        final JdiscBindingsConfig discBindingsConfig;

        public ApplicationContext(com.yahoo.container.jdisc.JdiscBindingsConfig discBindingsConfig) {
            this.discBindingsConfig = discBindingsConfig;
        }
    }

}
