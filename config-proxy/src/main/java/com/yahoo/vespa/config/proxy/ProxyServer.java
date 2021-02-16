// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import java.util.logging.Level;
import com.yahoo.log.LogSetup;
import com.yahoo.log.event.Event;
import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.vespa.config.RawConfig;
import com.yahoo.vespa.config.protocol.JRTServerConfigRequest;
import com.yahoo.vespa.config.proxy.filedistribution.FileDistributionAndUrlDownload;
import com.yahoo.yolean.system.CatchSignals;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static com.yahoo.vespa.config.proxy.Mode.ModeName.DEFAULT;

/**
 * A proxy server that handles RPC config requests. The proxy can run in two modes:
 * 'default' and 'memorycache', where the last one will not get config from an upstream
 * config source, but will serve config from memory cache only.
 *
 * @author hmusum
 */
public class ProxyServer implements Runnable {

    private static final DaemonThreadFactory threadFactory = new DaemonThreadFactory("ProxyServer");
    private static final int DEFAULT_RPC_PORT = 19090;
    private static final int JRT_TRANSPORT_THREADS = 4;
    static final String DEFAULT_PROXY_CONFIG_SOURCES = "tcp/localhost:19070";

    private final static Logger log = Logger.getLogger(ProxyServer.class.getName());
    private final AtomicBoolean signalCaught = new AtomicBoolean(false);
    private final Supervisor supervisor;

    private final ConfigProxyRpcServer rpcServer;
    private ConfigSourceSet configSource;

    private volatile ConfigSourceClient configClient;

    private final MemoryCache memoryCache;
    private final FileDistributionAndUrlDownload fileDistributionAndUrlDownload;

    private volatile Mode mode = new Mode(DEFAULT);

    ProxyServer(Spec spec, ConfigSourceSet source, MemoryCache memoryCache, ConfigSourceClient configClient) {
        this.configSource = source;
        supervisor = new Supervisor(new Transport("proxy-server", JRT_TRANSPORT_THREADS)).useSmallBuffers();
        log.log(Level.FINE, "Using config source '" + source);
        this.memoryCache = memoryCache;
        this.rpcServer = createRpcServer(spec);
        this.configClient = (configClient == null) ? createRpcClient(rpcServer, source, memoryCache) : configClient;
        this.fileDistributionAndUrlDownload = new FileDistributionAndUrlDownload(supervisor, source);
    }

    @Override
    public void run() {
        if (rpcServer != null) {
            Thread t = threadFactory.newThread(rpcServer);
            t.setName("RpcServer");
            t.start();
        }
    }

    RawConfig resolveConfig(JRTServerConfigRequest req) {
        // Calling getConfig() will either return with an answer immediately or
        // create a background thread that retrieves config from the server and
        // calls updateSubscribers when new config is returned from the config source.
        // In the last case the method below will return null.
        return configClient.getConfig(RawConfig.createFromServerRequest(req), req);
    }

    static boolean configOrGenerationHasChanged(RawConfig config, JRTServerConfigRequest request) {
        return (config != null && ( ! config.hasEqualConfig(request) || config.hasNewerGeneration(request)));
    }

    Mode getMode() {
        return mode;
    }

    void setMode(String modeName) {
        if (modeName.equals(this.mode.name())) return;

        Mode oldMode = this.mode;
        Mode newMode = new Mode(modeName);
        switch (newMode.getMode()) {
            case MEMORYCACHE:
                configClient.shutdownSourceConnections();
                configClient = new MemoryCacheConfigClient(memoryCache);
                this.mode = new Mode(modeName);
                break;
            case DEFAULT:
                flush();
                configClient = createRpcClient(rpcServer, configSource, memoryCache);
                this.mode = new Mode(modeName);
                break;
            default:
                throw new IllegalArgumentException("Cannot set invalid mode '" + modeName + "'");
        }
        log.log(Level.INFO, "Switched from '" + oldMode.name().toLowerCase() + "' mode to '" + getMode().name().toLowerCase() + "' mode");
    }

    private ConfigProxyRpcServer createRpcServer(Spec spec) {
        return  (spec == null) ? null : new ConfigProxyRpcServer(this, supervisor, spec); // TODO: Try to avoid first argument being 'this'
    }

    private static RpcConfigSourceClient createRpcClient(RpcServer rpcServer, ConfigSourceSet source, MemoryCache memoryCache) {
        return new RpcConfigSourceClient(rpcServer, source, memoryCache);
    }

    private void setupSignalHandler() {
        CatchSignals.setup(signalCaught); // catch termination and interrupt signals
    }

    private void waitForShutdown() {
        synchronized (signalCaught) {
            while (!signalCaught.get()) {
                try {
                    signalCaught.wait();
                } catch (InterruptedException e) {
                    // empty
                }
            }
        }
        ExecutorService executor = Executors.newSingleThreadExecutor(threadFactory);
        Callable<String> stopper = () -> { stop(); return "clean shutdown"; };
        Future<String> future = executor.submit(stopper);
        try {
            String result = future.get(5, TimeUnit.SECONDS);
            Event.stopping("configproxy", result);
        } catch (Exception e) {
            System.exit(1);
        }
        System.exit(0);
    }

    public static void main(String[] args) {
        /* Initialize the log handler */
        LogSetup.clearHandlers();
        LogSetup.initVespaLogging("configproxy");

        Properties properties = getSystemProperties();

        int port = DEFAULT_RPC_PORT;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        Event.started("configproxy");

        ConfigSourceSet configSources = new ConfigSourceSet(properties.configSources);
        ProxyServer proxyServer = new ProxyServer(new Spec(null, port), configSources, new MemoryCache(), null);
        // catch termination and interrupt signal
        proxyServer.setupSignalHandler();
        Thread proxyserverThread = threadFactory.newThread(proxyServer);
        proxyserverThread.setName("configproxy");
        proxyserverThread.start();
        proxyServer.waitForShutdown();
    }

    static Properties getSystemProperties() {
        final String[] inputConfigSources = System.getProperty("proxyconfigsources", DEFAULT_PROXY_CONFIG_SOURCES).split(",");
        return new Properties(inputConfigSources);
    }

    static class Properties {
        final String[] configSources;

        Properties(String[] configSources) {
            this.configSources = configSources;
        }
    }

    // Cancels all config instances and flushes the cache. When this method returns,
    // the cache will not be updated again before someone calls getConfig().
    private synchronized void flush() {
        memoryCache.clear();
        configClient.cancel();
    }

    void stop() {
        Event.stopping("configproxy", "shutdown rpcServer");
        if (rpcServer != null) rpcServer.shutdown();
        Event.stopping("configproxy", "cancel configClient");
        if (configClient != null) configClient.cancel();
        Event.stopping("configproxy", "flush");
        flush();
        Event.stopping("configproxy", "close fileDistribution");
        fileDistributionAndUrlDownload.close();
        Event.stopping("configproxy", "stop complete");
    }

    MemoryCache getMemoryCache() {
        return memoryCache;
    }

    String getActiveSourceConnection() {
        return configClient.getActiveSourceConnection();
    }

    List<String> getSourceConnections() {
        return configClient.getSourceConnections();
    }

    void updateSourceConnections(List<String> sources) {
        configSource = new ConfigSourceSet(sources);
        flush();
        configClient = createRpcClient(rpcServer, configSource, memoryCache);
    }

    DelayedResponses delayedResponses() {
        return configClient.delayedResponses();
    }

}
