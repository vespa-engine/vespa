// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.server;

import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ErrorCode;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Method;
import com.yahoo.jrt.MethodHandler;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringArray;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.TargetWatcher;
import com.yahoo.jrt.Task;
import com.yahoo.jrt.Transport;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Slobrok {

    Supervisor orb;
    Acceptor listener;
    private Map<String,String> services = new HashMap<>();
    List<FetchMirror> pendingFetch = new ArrayList<>();
    Map<String, Target> targets = new HashMap<>();
    TargetMonitor          monitor      = new TargetMonitor();
    int                    gencnt       = 1;

    public String lookup(String name) {
        return services.get(name);
    }


    public Slobrok(int port) throws ListenFailedException {
        // NB: rpc must be single-threaded
        orb = new Supervisor(new Transport("slobrok-" + port, 1)).setDropEmptyBuffers(true);
        registerMethods();
        try {
            listener = orb.listen(new Spec(port));
        } catch (ListenFailedException e) {
            orb.transport().shutdown().join();
            throw e;
        }
    }

    public Slobrok() throws ListenFailedException {
        this(0);
    }

    public int port() {
        return listener.port();
    }

    public void stop() {
        orb.transport().shutdown().join();
        listener.shutdown().join();
    }

    public String configId() {
        return "raw:slobrok[1]\n" +
               "slobrok[0].connectionspec \"" + new Spec("localhost", listener.port()).toString() + "\"\n";
    }

    private void updated() {
        gencnt++;
        if (gencnt == 0) {
            gencnt++;
        }
        handleFetchMirrorFlush();
    }

    private void handleRegisterCallbackDone(Request req, String name, String spec, Target target){
        String stored = services.get(name);
        if (stored != null) { // too late
            if ( ! stored.equals(spec)) {
                req.setError(ErrorCode.METHOD_FAILED, "service '" + name + "' registered with another spec");
            }
            req.returnRequest();
            target.close();
            return;
        }
        target.setContext(name);
        target.addWatcher(monitor);
        services.put(name, spec);
        targets.put(name, target);
        req.returnRequest();
        updated();
    }

    private void handleTargetDown(Target target) {
        String name = (String) target.getContext();
        targets.remove(name);
        services.remove(name);
        updated();
    }

    private void dumpServices(Request req) {
        List<String> names = new ArrayList<>();
        List<String> specs = new ArrayList<>();
        for (Map.Entry<String,String> entry : services.entrySet()) {
            names.add(entry.getKey());
            specs.add(entry.getValue());
        }
        req.returnValues().add(new StringArray(names.toArray(new String[names.size()])));
        req.returnValues().add(new StringArray(specs.toArray(new String[specs.size()])));
        req.returnValues().add(new Int32Value(gencnt));
    }

    private void handleFetchMirrorTimeout(FetchMirror fetch) {
        pendingFetch.remove(fetch);
        fetch.req.returnValues().add(new StringArray(new String[0]));
        fetch.req.returnValues().add(new StringArray(new String[0]));
        fetch.req.returnValues().add(new Int32Value(gencnt));
        fetch.req.returnRequest();
    }

    private void handleFetchMirrorFlush() {
        for (FetchMirror fetch : pendingFetch) {
            fetch.task.kill();
            dumpServices(fetch.req);
            fetch.req.returnRequest();
        }
        pendingFetch.clear();
    }

    private void registerMethods() {
        orb.addMethod(new Method("slobrok.registerRpcServer", "ss", "",
                                 new MethodHandler() {
                                     public void invoke(Request req) {
                                         rpc_register(req);
                                     }
                                 })
                      .methodDesc("Register a rpcserver")
                      .paramDesc(0, "name", "RpcServer name")
                      .paramDesc(1, "spec", "The connection specification"));
        orb.addMethod(new Method("slobrok.unregisterRpcServer", "ss", "",
                                 new MethodHandler() {
                                     public void invoke(Request req) {
                                         rpc_unregister(req);
                                     }
                                 })
                      .methodDesc("Unregister a rpcserver")
                      .paramDesc(0, "name", "RpcServer name")
                      .paramDesc(1, "spec", "The connection specification"));

        orb.addMethod(new Method("slobrok.incremental.fetch", "ii", "iSSSi",
                                 new MethodHandler() {
                                     public void invoke(Request req) {
                                         rpc_fetchIncremental(req);
                                     }
                                 })
                      .methodDesc("Fetch or update mirror of name to spec map")
                      .paramDesc(0, "gencnt", "generation already known by client")
                      .paramDesc(1, "timeout", "How many milliseconds to wait for changes"
                                 + "before returning if nothing has changed (max=10000)")
                      .returnDesc(0, "oldgen", "diff from generation already known by client")
                      .returnDesc(1, "removed", "Array of RpcServer names to remove")
                      .returnDesc(2, "names", "Array of RpcServer names with new values")
                      .returnDesc(3, "specs", "Array of connection specifications (same order)")
                      .returnDesc(4, "newgen", "Generation count for new version of the map"));
    }

    private void rpc_register(Request req) {
        String name = req.parameters().get(0).asString();
        String spec = req.parameters().get(1).asString();
        String stored = services.get(name);
        if (stored == null) {
            new RegisterCallback(req, name, spec);
        } else {
            if ( ! stored.equals(spec))
                req.setError(ErrorCode.METHOD_FAILED, "service '" + name + "' registered with another spec");
        }
    }

    private void rpc_unregister(Request req) {
        String name = req.parameters().get(0).asString();
        String spec = req.parameters().get(1).asString();
        String stored = services.get(name);
        if (stored != null) {
            if (stored.equals(spec)) {
                Target target = targets.remove(name);
                target.removeWatcher(monitor);
                services.remove(name);
                target.close();
                updated();
            } else {
                req.setError(ErrorCode.METHOD_FAILED,
                             "service '" + name + "' registered with another spec");
            }
        }
    }

    private void rpc_fetchIncremental(Request req) {
        int gencnt  = req.parameters().get(0).asInt32();
        int timeout = req.parameters().get(1).asInt32();

        // for now, always make "full diff" from generation 0
        req.returnValues().add(new Int32Value(0));
        req.returnValues().add(new StringArray(new String[0]));

        if (gencnt == this.gencnt) {
            pendingFetch.add(new FetchMirror(req, timeout));
        } else {
            dumpServices(req);
        }
    }

    private class RegisterCallback implements RequestWaiter {

        Request registerReq;
        String  name;
        String  spec;
        Target  target;

        public RegisterCallback(Request req, String name, String spec) {
            req.detach();
            registerReq = req;
            this.name = name;
            this.spec = spec;
            target = orb.connect(new Spec(spec));
            Request cbReq = new Request("slobrok.callback.listNamesServed");
            target.invokeAsync(cbReq, Duration.ofSeconds(5), this);
        }

        @Override
        public void handleRequestDone(Request req) {
            if ( ! req.checkReturnTypes("S")) {
                registerReq.setError(ErrorCode.METHOD_FAILED, "error during register callback: " + req.errorMessage());
                registerReq.returnRequest();
                target.close();
                return;
            }
            String[] names = req.returnValues().get(0).asStringArray();
            boolean found = false;
            for (String n : names) {
                if (n.equals(name)) {
                    found = true;
                }
            }
            if (!found) {
                registerReq.setError(ErrorCode.METHOD_FAILED, "register failed: served names does not contain name");
                registerReq.returnRequest();
                target.close();
                return;
            }
            handleRegisterCallbackDone(registerReq, name, spec, target);
        }
    }

    private class FetchMirror implements Runnable {
        public final Request req;
        public final Task task;

        public FetchMirror(Request req, int timeout) {
            req.detach();
            this.req = req;
            task = orb.transport().selectThread().createTask(this);
            task.schedule(((double)timeout)/1000.0);
        }
        public void run() { // timeout
            handleFetchMirrorTimeout(this);
        }
    }

    private class TargetMonitor implements TargetWatcher {
        public void notifyTargetInvalid(Target target) {
            handleTargetDown(target);
        }
    }

}
