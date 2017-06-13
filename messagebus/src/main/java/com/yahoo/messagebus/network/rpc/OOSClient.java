// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * This class keeps track of OOS information obtained from a single server. This class is used by the OOSManager class.
 * Note that since this class is only used inside the transport thread it has no synchronization. Using it directly will
 * lead to race conditions and possible crashes.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class OOSClient implements Runnable, RequestWaiter {

    private Supervisor orb;
    private Target target = null;
    private Request request = null;
    private boolean requestDone = false;
    private Spec spec;
    private Task task;
    private List<String> oosList = new ArrayList<String>();
    private int requestGen = 0;
    private int listGen = 0;
    private int dumpGen = 0;
    private boolean shutdown = false;

    /**
     * Create a new OOSClient polling oos information from the given server.
     *
     * @param orb  The object used for RPC operations.
     * @param spec The fnet connect spec for oos server.
     */
    public OOSClient(Supervisor orb, Spec spec) {
        this.orb = orb;
        this.spec = spec;

        task = this.orb.transport().createTask(this);
        task.scheduleNow();
    }

    /**
     * Handle a server reply.
     */
    private void handleReply() {
        if (!request.checkReturnTypes("Si")) {
            if (target != null) {
                target.close();
                target = null;
            }
            task.schedule(1.0);
            return;
        }

        Values ret = request.returnValues();
        int retGen = ret.get(1).asInt32();
        if (requestGen != retGen) {
            List<String> oos = new ArrayList<String>();
            oos.addAll(Arrays.asList(ret.get(0).asStringArray()));
            oosList = oos;
            requestGen = retGen;
            listGen = retGen;
        }
        task.schedule(0.1);
    }

    /**
     * Handle server (re)connect.
     */
    private void handleConnect() {
        if (target == null) {
            target = orb.connect(spec);
            requestGen = 0;
        }
    }

    /**
     * Handle server invocation.
     */
    private void handleInvoke() {
        if (target == null) {
            throw new IllegalStateException("Attempting to invoke a request on a null target.");
        }
        request = new Request("fleet.getOOSList");
        request.parameters().add(new Int32Value(requestGen));
        request.parameters().add(new Int32Value(60000));
        target.invokeAsync(request, 70.0, this);
    }

    /**
     * Implements runnable. Performs overall server poll logic.
     */
    public void run() {
        if (shutdown) {
            task.kill();
            if (target != null) {
                target.close();
            }
        } else if (requestDone) {
            requestDone = false;
            handleReply();
        } else {
            handleConnect();
            handleInvoke();
        }
    }

    /**
     * Shut down this OOS client. Invoking this method will take down any active connections and block further activity
     * from this object.
     */
    public void shutdown() {
        shutdown = true;
        task.scheduleNow();
    }

    /**
     * From FRT_IRequestWait, picks up server replies.
     *
     * @param request The request that has completed.
     */
    public void handleRequestDone(Request request) {
        if (request != this.request || requestDone) {
            throw new IllegalStateException("Multiple invocations of RequestDone().");
        }
        requestDone = true;
        task.scheduleNow();
    }

    /**
     * Obtain the connect spec of the OOS server this client is talking to.
     *
     * @return OOS server connect spec
     */
    public Spec getSpec() {
        return spec;
    }

    /**
     * Check if this client has changed. A client has changed if it  has obtain now information after the dumpState
     * method was last invoked.
     *
     * @return True is this client has changed.
     */
    public boolean isChanged() {
        return listGen != dumpGen;
    }

    /**
     * Returns whether or not this client has receieved any reply at all from the server it is connected to.
     *
     * @return True if initial request has returned.
     */
    public boolean isReady() {
        return listGen != 0;
    }

    /**
     * Dump the current oos information known by this client into the given string set.
     *
     * @param dst The object used to aggregate oos information.
     */
    public void dumpState(Set<String> dst) {
        dst.addAll(oosList);
        dumpGen = listGen;
    }
}
