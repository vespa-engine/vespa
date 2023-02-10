// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.api;

import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.MethodHandler;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Task;
import com.yahoo.jrt.TransportThread;
import com.yahoo.jrt.Values;
import com.yahoo.security.tls.Capability;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A Register object is used to register and unregister services with
 * a slobrok cluster.
 *
 * The register/unregister operations performed against this object
 * are stored in a to-do list that will be performed asynchronously
 * against the slobrok cluster as soon as possible.
 */
public class Register {

    private static Logger log = Logger.getLogger(Register.class.getName());

    private static final String REGISTER_METHOD_NAME = "slobrok.registerRpcServer";
    private static final String UNREGISTER_METHOD_NAME = "slobrok.unregisterRpcServer";

    private Supervisor    orb;
    private SlobrokList   slobroks;
    private String        currSlobrok;
    private final String  mySpec;
    private BackOffPolicy backOff;
    private boolean       reqDone    = false;
    private List<String>  names      = new ArrayList<>();
    private List<String>  pending    = new ArrayList<>();
    private List<String>  unreg      = new ArrayList<>();
    private final TransportThread transportThread;
    private Task          updateTask = null;
    private RequestWaiter reqWait    = null;
    private Target        target     = null;
    private Request       req        = null;
    private String        name       = null;
    private Method        m_list     = null;
    private Method        m_unreg    = null;

    /** Whether the last registerRpcServer for the name was a success, or null for the first. */
    private final Map<String, Boolean> lastRegisterSucceeded = new HashMap<>();

    /**
     * Remove all instances of name from list.
     */
    private void discard(List<String> list, String name) {
        List<String> tmp = new ArrayList<>();
        tmp.add(name);
        list.removeAll(tmp);
    }

    /**
     * Create a new Register using the given Supervisor, slobrok
     * connect specs, hostname and port
     *
     * @param orb the Supervisor to use
     * @param slobroks slobrok connect spec list
     * @param spec the Spec representing hostname and port for this host
     * @param bop custom backoff policy, mostly useful for testing
     */
    public Register(Supervisor orb, SlobrokList slobroks, Spec spec, BackOffPolicy bop) {
        this.orb = orb;
        this.slobroks = slobroks;
        this.backOff = bop;
        mySpec = spec.toString();
        transportThread = orb.transport().selectThread();
        updateTask = transportThread.createTask(this::handleUpdate);
        reqWait = new RequestWaiter() {
                public void handleRequestDone(Request req) {
                    reqDone = true;
                    updateTask.scheduleNow();
                    transportThread.wakeup_if_not_self();
                }
            };
        m_list = new Method("slobrok.callback.listNamesServed",
                            "", "S", new MethodHandler() {
                                    public void invoke(Request req) {
                                        handleRpcList(req);
                                    }
                                })
            .requireCapabilities(Capability.CLIENT__SLOBROK_API)
            .methodDesc("List rpcserver names")
            .returnDesc(0, "names",
                        "The rpcserver names this server wants to serve");
        orb.addMethod(m_list);
        m_unreg = new Method("slobrok.callback.notifyUnregistered",
                             "s", "", new MethodHandler() {
                                     public void invoke(Request req) {
                                         handleRpcUnreg(req);
                                     }
                                 })
            .requireCapabilities(Capability.CLIENT__SLOBROK_API)
            .methodDesc("Notify a server about removed registration")
            .paramDesc(0, "name", "RpcServer name");
        orb.addMethod(m_unreg);
        updateTask.scheduleNow();
    }

    /**
     * Create a new Register using the given Supervisor, slobrok
     * connect specs, hostname and port
     *
     * @param orb the Supervisor to use
     * @param slobroks slobrok connect spec list
     * @param spec the Spec representing hostname and port for this host
     */
    public Register(Supervisor orb, SlobrokList slobroks, Spec spec) {
        this(orb, slobroks, spec, new BackOff());
    }

    /**
     * Create a new Register using the given Supervisor, slobrok
     * connect specs, hostname and port
     *
     * @param orb the Supervisor to use
     * @param slobroks slobrok connect spec list
     * @param myHost the hostname of this host
     * @param myPort the port number we are listening to
     */
    public Register(Supervisor orb, SlobrokList slobroks, String myHost, int myPort) {
        this(orb, slobroks, new Spec(myHost, myPort));
    }


    /**
     * Shut down the Register. This will close any open connections
     * and stop the regular re-registration.
     */
    public void shutdown() {
        updateTask.kill();
        transportThread.perform(this::handleShutdown);
    }

    /**
     * Register a service with the slobrok cluster.
     *
     * @param name service name
     */
    public synchronized void registerName(String name) {
        if (names.indexOf(name) >= 0) {
            return;
        }
        names.add(name);
        pending.add(name);
        discard(unreg, name);
        updateTask.scheduleNow();
        transportThread.wakeup();
    }

    /**
     * Unregister a service with the slobrok cluster
     *
     * @param name service name
     */
    public synchronized void unregisterName(String name) {
        discard(names, name);
        discard(pending, name);
        unreg.add(name);
        updateTask.scheduleNow();
        transportThread.wakeup();
    }

    /**
     * Invoked by the update task.
     **/
    private void handleUpdate() {
        if (reqDone) {
            reqDone = false;

            boolean logOnSuccess = false;
            boolean logOnFailure = true;
            synchronized (this) {
                if (req.methodName().equals(UNREGISTER_METHOD_NAME)) {
                    logOnSuccess = true;
                    // Why is this remove() here and not in unregisterName? Because at that time there may be
                    // an in-flight request for the registration of name, and in case handleUpdate() would
                    // anyway have to have special code for handling a removed name, e.g. testing for name
                    // being in names which is O(N).
                    lastRegisterSucceeded.remove(name);
                } else {
                    final Boolean lastSucceeded = lastRegisterSucceeded.get(name);
                    if (lastSucceeded == null) {
                        logOnSuccess = true;
                        logOnFailure = false;
                    } else if (lastSucceeded != !req.isError()) {
                        logOnSuccess = true;
                    }
                    lastRegisterSucceeded.put(name, !req.isError());
                }
            }

            if (req.isError()) {
                 if (req.errorCode() != ErrorCode.METHOD_FAILED) {
                     if (logOnFailure) {
                         log.log(Level.INFO, logMessagePrefix() + " failed, will disconnect: " + req.errorMessage() + " (code " + req.errorCode() + ")");
                     }
                    target.close();
                    target = null;
                } else {
                    log.log(Level.WARNING, logMessagePrefix() + " failed: " + req.errorMessage());
                }
            } else {
                log.log(logOnSuccess ? Level.INFO : Level.FINE, () -> logMessagePrefix() + " completed successfully");
                backOff.reset();
            }
            req = null;
            name = null;
        }
        if (req != null) {
            log.log(Level.FINEST, "req in progress");
            return; // current request still in progress
        }
        if (target != null && ! slobroks.contains(currSlobrok)) {
            log.log(Level.INFO, "[RPC @ " + mySpec + "] location broker " + currSlobrok + " removed, will disconnect and use one of: "+slobroks);
            target.close();
            target = null;
        }
        if (target == null) {
            currSlobrok = slobroks.nextSlobrokSpec();
            if (currSlobrok == null) {
                double delay = backOff.get();
                Level level = Level.FINE;
                if (backOff.shouldInform(delay)) level = Level.INFO;
                if (backOff.shouldWarn(delay)) level = Level.WARNING;
                log.log(level, "[RPC @ " + mySpec + "] no location brokers available, retrying: "+slobroks+" (in " + delay + " seconds)");
                updateTask.schedule(delay);
                return;
            }
            lastRegisterSucceeded.clear();
            target = orb.connect(new Spec(currSlobrok));
            String namesString = null;
            final boolean logFine = log.isLoggable(Level.FINE);
            synchronized (this) {
                if (logFine) {
                    // 'names' must only be accessed in a synchronized(this) block
                    namesString = names.toString();
                }
                pending.clear();
                pending.addAll(names);
            }

            if (logFine) {
                log.log(Level.FINE, "[RPC @ " + mySpec + "] Connect to location broker " + currSlobrok +
                        " and reregister all service names: " + namesString);
            }
        }

        synchronized (this) {
            if (unreg.size() > 0) {
                name = unreg.remove(unreg.size() - 1);
                req = new Request(UNREGISTER_METHOD_NAME);
            } else if (pending.size() > 0) {
                name = pending.remove(pending.size() - 1);
                req = new Request(REGISTER_METHOD_NAME);
            } else {
                pending.addAll(names);
                log.log(Level.FINE, "[RPC @ " + mySpec + "] Reregister all service names in 30 seconds: " + names);
                updateTask.schedule(30.0);
                return;
            }
        }

        req.parameters().add(new StringValue(name));
        req.parameters().add(new StringValue(mySpec));
        log.log(Level.FINE, logMessagePrefix() + " now");
        target.invokeAsync(req, Duration.ofSeconds(35), reqWait);
    }

    private String logMessagePrefix() {
        return "[RPC @ " + mySpec + "] "
                + (req.methodName().equals(UNREGISTER_METHOD_NAME) ? "unregistering " : "registering ")
                + name + " with location broker " + currSlobrok;
    }

    private synchronized void handleRpcList(Request req) {
        Values dst = req.returnValues();
        dst.add(new StringArray(names.toArray(new String[names.size()])));
    }

    private void handleRpcUnreg(Request req) {
        log.log(Level.WARNING, "unregistered name " + req.parameters().get(0).asString());
    }

    /**
     * Invoked from the transport thread, requested by the shutdown
     * method.
     **/
    private void handleShutdown() {
        orb.removeMethod(m_list);
        orb.removeMethod(m_unreg);
        if (req != null) {
            req.abort();
            req = null;
        }
        if (target != null) {
            target.close();
            target = null;
        }
    }

}
