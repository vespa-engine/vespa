// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.api;

import com.yahoo.jrt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;
import java.util.logging.Level;

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

    private Supervisor    orb;
    private SlobrokList   slobroks;
    private String        currSlobrok;
    private String        mySpec;
    private BackOffPolicy backOff;
    private boolean       reqDone    = false;
    private List<String>     names      = new ArrayList<>();
    private List<String> pending    = new ArrayList<>();
    private List<String>     unreg      = new ArrayList<>();
    private Task          updateTask = null;
    private RequestWaiter reqWait    = null;
    private Target        target     = null;
    private Request       req        = null;
    private Method        m_list     = null;
    private Method        m_unreg    = null;

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
        updateTask = orb.transport().createTask(new Runnable() {
                public void run() { handleUpdate(); }
            });
        reqWait = new RequestWaiter() {
                public void handleRequestDone(Request req) {
                    reqDone = true;
                    updateTask.scheduleNow();
                }
            };
        m_list = new Method("slobrok.callback.listNamesServed",
                            "", "S", new MethodHandler() {
                                    public void invoke(Request req) {
                                        handleRpcList(req);
                                    }
                                })
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
        orb.transport().perform(new Runnable() {
                public void run() { handleShutdown(); }
            });
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
    }

    /**
     * Invoked by the update task.
     **/
    private void handleUpdate() {
        if (reqDone) {
            reqDone = false;
            if (req.isError()) {
                if (req.errorCode() != ErrorCode.METHOD_FAILED) {
                    log.log(Level.FINE, "register failed: " + req.errorMessage() + " (code " + req.errorCode() + ")");
                    target.close();
                    target = null;
                } else {
                    log.log(Level.WARNING, "register failed: " + req.errorMessage() + " (code " + req.errorCode() + ")");
                }
            } else {
                backOff.reset();
            }
            req = null;
        }
        if (req != null) {
            log.log(Level.FINEST, "req in progress");
            return; // current request still in progress
        }
        if (target != null && ! slobroks.contains(currSlobrok)) {
            target.close();
            target = null;
        }
        if (target == null) {
            currSlobrok = slobroks.nextSlobrokSpec();
            if (currSlobrok == null) {
                double delay = backOff.get();
                updateTask.schedule(delay);
                if (backOff.shouldWarn(delay))
                    log.log(Level.WARNING, "slobrok connection problems (retry in " + delay + " seconds) to: " + slobroks);
                else
                    log.log(Level.FINE, "slobrok retry in " + delay + " seconds");
                return;
            }
            target = orb.connect(new Spec(currSlobrok));
            synchronized (this) {
                pending.clear();
                pending.addAll(names);
            }
        }
        boolean unregister = false;
        String  name;
        synchronized (this) {
            if (unreg.size() > 0) {
                name = unreg.remove(unreg.size() - 1);
                unregister = true;
            } else if (pending.size() > 0) {
                name = pending.remove(pending.size() - 1);
            } else {
                pending.addAll(names);
                log.log(Level.FINE, "done, reschedule in 30s");
                updateTask.schedule(30.0);
                return;
            }
        }

        if (unregister) {
            req = new Request("slobrok.unregisterRpcServer");
            req.parameters().add(new StringValue(name));
            log.log(Level.FINE, "unregister [" + name + "]");
            req.parameters().add(new StringValue(mySpec));
            target.invokeAsync(req, 35.0, reqWait);
        } else { // register
            req = new Request("slobrok.registerRpcServer");
            req.parameters().add(new StringValue(name));
            log.log(Level.FINE, "register [" + name + "]");
            req.parameters().add(new StringValue(mySpec));
            target.invokeAsync(req, 35.0, reqWait);
        }
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
