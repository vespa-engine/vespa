// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.ErrorCode;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.routing.RoutingContext;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.logging.Logger;

/**
 * Abstract class for routing policies that need asynchronous initialization.
 * This is recommended if the routing policy needs configuration, or synchronization with
 * other sources. If this policy is not used in those cases, the messagebus thread might hang
 * waiting for the other sources, causing messages to other routes to be blocked.
 */
public abstract class AsyncInitializationPolicy implements DocumentProtocolRoutingPolicy, Runnable {

    protected enum InitState {
        NOT_STARTED,
        RUNNING,
        DONE
    }

    private static final Logger log = Logger.getLogger(AsyncInitializationPolicy.class.getName());

    private InitState initState;
    private ScheduledThreadPoolExecutor executor;
    private Exception initException;
    private boolean syncInit = true;

    public static Map<String, String> parse(String param) {
        Map<String, String> map = new TreeMap<>();

        if (param != null) {
            String[] p = param.split(";");
            for (String s : p) {
                String[] keyValue = s.split("=");

                if (keyValue.length == 1) {
                    map.put(keyValue[0], "true");
                } else if (keyValue.length == 2) {
                    map.put(keyValue[0], keyValue[1]);
                }
            }
        }

        return map;
    }

    AsyncInitializationPolicy() {
        initState = InitState.NOT_STARTED;
    }

    void needAsynchronousInitialization() {
        syncInit = false;
    }

    public abstract void init();

    public abstract void doSelect(RoutingContext routingContext);

    private synchronized void checkStartInit() {
        if (initState == InitState.NOT_STARTED) {
            if (syncInit) {
                init();
                initState = InitState.DONE;
            } else {
                executor = new ScheduledThreadPoolExecutor(1);
                executor.execute(this);
                initState = InitState.RUNNING;
            }
        }
    }

    @Override
    public void select(RoutingContext routingContext) {
        synchronized (this) {
            if (initException != null) {
                Reply reply = new EmptyReply();
                reply.addError(new com.yahoo.messagebus.Error(ErrorCode.POLICY_ERROR, "Policy threw exception during init:" + exceptionMessageWithTrace(initException)));
                routingContext.setReply(reply);
                return;
            }

            checkStartInit();

            if (initState == InitState.RUNNING) {
                Reply reply = new EmptyReply();
                reply.addError(new com.yahoo.messagebus.Error(ErrorCode.SESSION_BUSY, "Policy is waiting to be initialized."));
                routingContext.setReply(reply);
                return;
            }
        }

        doSelect(routingContext);
    }

    public void run() {
        try {
            init();
        } catch (Exception e) {
            log.log(LogLevel.WARNING,"Init threw exception",e);
            initException = e;
        }

        synchronized (this) {
            initState = InitState.DONE;
            this.notifyAll();
        }
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }


    private static String exceptionMessageWithTrace(Exception e) {
        StringWriter sw = new StringWriter();
        try (PrintWriter pw = new PrintWriter(sw)) {
            e.printStackTrace(pw);
            pw.flush();
        }
        return sw.toString();

    }



}
