// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver;

import com.yahoo.io.FatalErrorHandler;
import com.yahoo.io.Listener;
import com.yahoo.log.LogLevel;
import com.yahoo.log.LogSetup;
import com.yahoo.log.event.Event;
import com.yahoo.logserver.handlers.HandlerThread;
import com.yahoo.logserver.handlers.LogHandler;
import com.yahoo.logserver.net.LogConnectionFactory;
import com.yahoo.logserver.net.control.Levels;
import com.yahoo.system.CatchSigTerm;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
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

    // the port is a String because we want to use it as the default
    // value of a System.getProperty().
    private static final String LISTEN_PORT = "19081";

    private int listenPort;
    private Listener listener;
    private final LogDispatcher dispatch;

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
     *
     * @param listenPort The port on which the logserver accepts log
     *                   messages.
     */
    public void initialize(int listenPort) {
        if (isInitialized) {
            throw new IllegalStateException(APPNAME + " already initialized");
        }

        this.listenPort = listenPort;

        // plugins
        registerPluginLoader(new BuiltinPluginLoader());

        // main listener
        listener = new Listener(APPNAME);
        listener.addSelectLoopPostHook(dispatch);
        listener.setFatalErrorHandler(fatalErrorHandler);
    }

    /**
     * Sets up the listen port and starts the Listener.  Then waits for
     * Listener to exit.
     */
    public void run() {
        try {
            listener.listen(new LogConnectionFactory(dispatch), listenPort);
            log.log(LogLevel.CONFIG, APPNAME + ".listenport=" + listenPort);
        } catch (IOException e) {
            log.log(LogLevel.ERROR, "Unable to initialize", e);
            return;
        }

        log.fine("Starting listener...");
        listener.start();
        Event.started(APPNAME);
        try {
            listener.join();
            log.fine("listener thread exited");
        } catch (InterruptedException e) {
            log.log(Level.WARNING, "Server was interrupted", e);
        }
    }

    private void setupSigTermHandler() {
        CatchSigTerm.setup(signalCaught); // catch termination signal
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
        System.out.println(" - " + APPNAME + ".listenport (" + LISTEN_PORT + ")");
        System.out.println(" - " + APPNAME + ".queue.size (" + HandlerThread.DEFAULT_QUEUESIZE + ")");
        System.out.println(" - logserver.default.loglevels (" + (new Levels()).toString() + ")");
        System.out.println();
    }

    public static void main(String[] args) {
        if (args.length > 0 && "-help".equals(args[0])) {
            help();
            System.exit(0);
        }

        String portString = System.getProperty(APPNAME + ".listenport", LISTEN_PORT);
        Server server = Server.getInstance();
        server.setupSigTermHandler();
        server.initialize(Integer.parseInt(portString));

        Thread t = new Thread(server, "logserver main");
        t.start();
        server.waitForShutdown();
    }
}
