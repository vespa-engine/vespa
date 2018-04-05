// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.common.collect.MapMaker;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.container.Container;
import com.yahoo.container.QrConfig;
import com.yahoo.container.Server;
import com.yahoo.container.core.ChainsConfig;
import com.yahoo.container.core.config.HandlersConfigurerDi;
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
import com.yahoo.jdisc.application.GuiceRepository;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogSetup;
import com.yahoo.osgi.OsgiImpl;
import com.yahoo.vespa.config.ConfigKey;
import com.yahoo.yolean.Exceptions;

import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.collections.CollectionUtil.first;

/**
 * @author Tony Vaagenes
 */
public final class ConfiguredApplication implements Application {

    public static final class ApplicationContext {

        final JdiscBindingsConfig discBindingsConfig;

        public ApplicationContext(com.yahoo.container.jdisc.JdiscBindingsConfig discBindingsConfig) {
            this.discBindingsConfig = discBindingsConfig;
        }
    }

    private static final Logger log = Logger.getLogger(ConfiguredApplication.class.getName());
    private static final Set<ClientProvider> startedClients = newWeakIdentitySet();

    private static final Set<ServerProvider> startedServers = Collections.newSetFromMap(new IdentityHashMap<>());
    private final SubscriberFactory subscriberFactory;
    private final ContainerActivator activator;
    private final String configId;
    private final ContainerDiscApplication applicationWithLegacySetup;
    private final OsgiFramework osgiFramework;
    private final com.yahoo.jdisc.Timer timerSingleton;
    //TODO: FilterChainRepository should instead always be set up in the model.
    private final FilterChainRepository defaultFilterChainRepository =
            new FilterChainRepository(new ChainsConfig(new ChainsConfig.Builder()),
                                      new ComponentRegistry<>(),
                                      new ComponentRegistry<>(),
                                      new ComponentRegistry<>(),
                                      new ComponentRegistry<>());
    private final OsgiFramework restrictedOsgiFramework;
    private volatile int applicationSerialNo = 0;
    private HandlersConfigurerDi configurer;
    private ScheduledThreadPoolExecutor shutdownDeadlineExecutor;
    private Thread reconfigurerThread;
    private Thread portWatcher;
    private QrConfig qrConfig;

    static {
        LogSetup.initVespaLogging("Container");
        log.log(LogLevel.INFO, "Starting container");
    }

    /**
     * Do not delete this method even if its empty.
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
                                 SubscriberFactory subscriberFactory) throws ListenFailedException {
        this.activator = activator;
        this.osgiFramework = osgiFramework;
        this.timerSingleton = timer;
        this.subscriberFactory = subscriberFactory;
        this.configId = System.getProperty("config.id");
        this.restrictedOsgiFramework = new DisableOsgiFramework(new RestrictedBundleContext(osgiFramework.bundleContext()));
        Container.get().setOsgi(new OsgiImpl(osgiFramework));

        applicationWithLegacySetup = new ContainerDiscApplication(configId);
    }

    @Override
    public void start() {
        qrConfig = getConfig(QrConfig.class);
        hackToInitializeServer(qrConfig);

        ContainerBuilder builder = createBuilderWithGuiceBindings();
        configureComponents(builder.guiceModules().activate());

        intitializeAndActivateContainer(builder);
        startReconfigurerThread();
        portWatcher = new Thread(this::watchPortChange);
        portWatcher.setDaemon(true);
        portWatcher.start();
    }


    private static void hackToInitializeServer(QrConfig config) {
        try {
            Server.get().initialize(config);
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "Caught exception when initializing server. Exiting.", e);
            Runtime.getRuntime().halt(1);
        }
    }

    private <T extends ConfigInstance> T getConfig(Class<T> configClass) {
        Subscriber subscriber = subscriberFactory.getSubscriber(
                Collections.singleton(new ConfigKey<>(configClass, configId)));
        try {
            subscriber.waitNextGeneration();
            return configClass.cast(first(subscriber.config().values()));
        } finally {
            subscriber.close();
        }
    }

    private void watchPortChange() {
        Subscriber subscriber = subscriberFactory.getSubscriber(Collections.singleton(new ConfigKey<>(QrConfig.class, configId)));
        try {
            while (true) {
                subscriber.waitNextGeneration();
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

    private void intitializeAndActivateContainer(ContainerBuilder builder) {
        addHandlerBindings(builder, Container.get().getRequestHandlerRegistry(),
                           configurer.getComponent(ApplicationContext.class).discBindingsConfig);
        installServerProviders(builder);

        activator.activateContainer(builder); // TODO: .notifyTermination(.. decompose previous component graph ..)

        startClients();
        startAndStopServers();

        log.info("Switching to the latest deployed set of configurations and components. " +
                 "Application switch number: " + (applicationSerialNo++));
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
                    configurer.runOnceAndEnsureRegistryHackRun(builder.guiceModules().activate());
                    intitializeAndActivateContainer(builder);
                    if (qrConfig.restartOnDeploy()) break;
                } catch (ConfigInterruptedException | InterruptedException e) {
                    break;
                } catch (Exception | LinkageError e) { // LinkageError: OSGi problems
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

    private void configureComponents(Injector discInjector) {
        configurer = new HandlersConfigurerDi(
                subscriberFactory,
                Container.get(),
                configId,
                new Deconstructor(true),
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
        modules.install(applicationWithLegacySetup.getMbusBindings());
    }

    @Override
    public void stop() {
        startShutdownDeadlineExecutor();
        shutdownReconfigurerThread();

        configurer.shutdown(new Deconstructor(false));

        for (ServerProvider server : Container.get().getServerProviderRegistry().allComponents()) {
            if (startedServers.contains(server)) {
                closeServer(server);
            }
        }
        Container.get().shutdown();
    }

    private void shutdownReconfigurerThread() {
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
        shutdownDeadlineExecutor.schedule(new Runnable() {

            @Override
            public void run() {
                com.yahoo.protect.Process.logAndDie(
                        "Timed out waiting for application shutdown. Please check that all your request handlers " +
                                "drain their request content channels.", true);
            }
        }, delayMillis, TimeUnit.MILLISECONDS);
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

    private static <T> Set<T> newWeakIdentitySet() {
        Map<T, Boolean> weakIdentityHashMap = new MapMaker().weakKeys().makeMap();
        return Collections.newSetFromMap(weakIdentityHashMap);
    }

}
