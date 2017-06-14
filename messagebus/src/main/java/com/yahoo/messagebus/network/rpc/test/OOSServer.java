// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc.test;

import com.yahoo.jrt.*;
import com.yahoo.jrt.slobrok.api.SlobrokList;
import com.yahoo.jrt.slobrok.api.Register;
import com.yahoo.jrt.slobrok.server.Slobrok;

import java.util.ArrayList;
import java.util.List;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class OOSServer {
    private int getCnt = 1;
    private List<String> state = new ArrayList<String>();
    private Supervisor orb;
    private Register register;
    private Acceptor   listener;

    public OOSServer(Slobrok slobrok, String service, OOSState state) {
        orb = new Supervisor(new Transport());
        orb.addMethod(new Method("fleet.getOOSList", "ii", "Si",
                                 new MethodHandler() {
                                     public void invoke(Request request) {
                                         rpc_poll(request);
                                     }
                                 })
                      .methodDesc("Fetch OOS information.")
                      .paramDesc(0, "gencnt", "Generation already known by client.")
                      .paramDesc(1, "timeout", "How many milliseconds to wait for changes before returning if nothing has changed (max=10000).")
                      .returnDesc(0, "names", "List of services that are OOS (empty if generation has not changed).")
                      .returnDesc(1, "newgen", "Generation of the returned list."));
        try {
            listener = orb.listen(new Spec(0));
        }
        catch (ListenFailedException e) {
            orb.transport().shutdown().join();
            throw new RuntimeException(e);
        }
        SlobrokList slist = new SlobrokList();
        slist.setup(new String[] { new Spec("localhost", slobrok.port()).toString() });
        register = new Register(orb, slist, "localhost", listener.port());
        register.registerName(service);
        setState(state);
    }

    public void shutdown() {
        register.shutdown();
        listener.shutdown().join();
        orb.transport().shutdown().join();
    }

    public void setState(OOSState state) {
        List<String> newState = new ArrayList<String>();
        for (String service : state.getServices()) {
            if (state.isOOS(service)) {
                newState.add(service);
            }
        }
        synchronized(this) {
            this.state = newState;
            if (++getCnt == 0) {
                getCnt = 1;
            }
        }
    }

    private void rpc_poll(Request request) {
        synchronized(this) {
            request.returnValues()
                .add(new StringArray(state.toArray(new String[state.size()])))
                .add(new Int32Value(getCnt));
        }
    }

    public int getPort() {
        return listener.port();
    }
}
