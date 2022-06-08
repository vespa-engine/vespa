// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import ai.vespa.logserver.protocol.ArchiveLogMessagesMethod;
import ai.vespa.logserver.protocol.RpcServer;
import com.yahoo.io.FatalErrorHandler;
import com.yahoo.log.LogSetup;
import com.yahoo.log.event.Event;
import com.yahoo.logserver.handlers.HandlerThread;
import com.yahoo.logserver.handlers.LogHandler;
import com.yahoo.yolean.system.CatchSignals;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * This class implements the log server itself.  At present there is
 * no runtime configuration;  the server starts up, binds port 19081
 * and loads builtin plugins.
 *
 * @author Bjorn Borud
 * @author Stig Bakken
 */
public class Server implements Runnable {
    private final AtomicBoolean signalCaught = new AtomicBoolean(false);
    static final String APPNAME = "logserver";
    private static final Server instance = new Server();
    private static final Logger log = Logger.getLogger(Server.class.getName());
    private static final FatalErrorHandler fatalErrorHandler = new FatalErrorHandler();
    private static final HashMap<String, HandlerThread> handlerThreads = new HashMap<>();
    private static final HashMap<LogHandler, String> threadNameForHandler = new HashMap<>();

    static {
        LogSetup.initVespaLogging("ADM");
    }

    private static final int DEFAULT_RPC_LISTEN_PORT = 19080;

    private final LogDispatcher dispatch;
    private RpcServer rpcServer;

    private final boolean isInitialized;

    private Server() {
        dispatch = new LogDispatcher();
        dispatch.setBatchedMode(true);
        isInitialized = false;
    }

    public static Server getInstance() {
        return instance;
    }

    private HandlerThread getHandlerThread(String threadName) {
        threadName += " handler thread";
        HandlerThread ht = handlerThreads.get(threadName);
        if (ht == null) {
            ht = new HandlerThread(threadName);
            handlerThreads.put(threadName, ht);
            ht.setFatalErrorHandler(fatalErrorHandler);
            dispatch.registerLogHandler(ht);
        }
        return ht;
    }

    private void registerPluginLoader(PluginLoader loader) {
        loader.loadPlugins();
    }

    public void registerLogHandler(LogHandler lh, String threadName) {
        HandlerThread ht = getHandlerThread(threadName);
        ht.registerHandler(lh);
        threadNameForHandler.put(lh, threadName);
    }

    public void unregisterLogHandler(LogHandler lh) {
        String threadName = threadNameForHandler.get(lh);
        unregisterLogHandler(lh, threadName);
    }

    private void unregisterLogHandler(LogHandler lh, String threadName) {
        HandlerThread ht = getHandlerThread(threadName);
        ht.unregisterHandler(lh);
        threadNameForHandler.remove(lh);
    }

    public void registerFlusher(LogHandler lh) {
        Flusher.register(lh);
    }

    /**
     * Included only for consistency
     */
    public void unregisterFlusher(LogHandler lh) {
        /* NOP */
    }

    /**
     * Initialize the server and start up all its plugins,
     */
    public void initialize(int rpcListenPort) {
        if (isInitialized) {
            throw new IllegalStateException(APPNAME + " already initialized");
        }

        // plugins
        registerPluginLoader(new BuiltinPluginLoader());

        rpcServer = new RpcServer(rpcListenPort);
        rpcServer.addMethod(new ArchiveLogMessagesMethod(dispatch).methodDefinition());
    }

    /**
     * Sets up the listen port and starts the Listener.  Then waits for
     * Listener to exit.
     */
    @Override
    public void run() {
        log.fine("Starting rpc server...");
        rpcServer.start();
        Event.started(APPNAME);
    }

    private void setupSignalHandler() {
        CatchSignals.setup(signalCaught); // catch termination and interrupt signals
    }

    private void waitForShutdown() {
        synchronized (signalCaught) {
            while (! signalCaught.get()) {
                try {
                    signalCaught.wait();
                } catch (InterruptedException e) {
                    // empty
                }
            }
        }
        Event.stopping(APPNAME, "shutdown");
        rpcServer.close();
        dispatch.close();
        Event.stopped(APPNAME, 0, 0);
        System.exit(0);
    }

    static HashMap<LogHandler, String> threadNameForHandler() {
        return threadNameForHandler;
    }

    static void help() {
        System.out.println();
        System.out.println("System properties:");
        System.out.println(" - " + APPNAME + ".rpcListenPort (" + DEFAULT_RPC_LISTEN_PORT + ")");
        System.out.println(" - " + APPNAME + ".queue.size (" + HandlerThread.DEFAULT_QUEUESIZE + ")");
        System.out.println();
    }

    public static void main(String[] args) {
        if (args.length > 0 && "-help".equals(args[0])) {
            help();
            System.exit(0);
        }

        int rpcPort = Integer.parseInt(System.getProperty(APPNAME + ".rpcListenPort", Integer.toString(DEFAULT_RPC_LISTEN_PORT)));
        Server server = Server.getInstance();
        server.setupSignalHandler();
        server.initialize(rpcPort);

        Thread t = new Thread(server, "logserver main");
        t.start();
        server.waitForShutdown();
    }
}
