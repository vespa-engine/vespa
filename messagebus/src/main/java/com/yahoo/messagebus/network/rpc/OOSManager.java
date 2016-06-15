// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Task;
import com.yahoo.jrt.slobrok.api.Mirror;

import java.util.*;

/**
 * This class keeps track of OOS information. A set of servers having OOS information are identified by looking up a
 * service pattern in the slobrok. These servers are then polled for information. The information is compiled into a
 * local repository for fast lookup.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class OOSManager implements Runnable {

    // An internal flag that indicates whether or not this manager is disabled. This is used to short-circuit any
    // requests made when the service pattern is null.
    private boolean disabled;

    // Whether or not this manager has received status information from all connected clients.
    private boolean ready;

    // The JRT supervisor object.
    private final Supervisor orb;

    // The JRT slobrok mirror object.
    private final Mirror mirror;

    // A transport task object used for scheduling this.
    private Task task;

    // The service pattern used to resolve what services registered in slobrok resolve to OOS servers.
    private final String servicePattern;

    // A map of OOS clients that each poll a single OOS server. This map will contain an entry for each service that
    // the service pattern resolves to.
    private Map<String, OOSClient> clients = Collections.emptyMap();

    // A set of out-of-service service names.
    private volatile Set<String> oosSet;

    // The generation of the current slobrok resolve.
    private int slobrokGen = 0;

    // A local copy of the services that the service pattern resolved to after the previous slobrok lookup. This is used
    // to avoid updating the internal list every time slobrok's generation differs, but instead only when the service
    // pattern resolves to something different.
    private List<Mirror.Entry> services;

    /**
     * Create a new OOSManager. The given service pattern will be looked up in the given slobrok mirror. The resulting
     * set of services will be polled for oos information.
     *
     * @param orb            The object used for RPC operations.
     * @param mirror         The slobrok mirror.
     * @param servicePattern The service pattern for oos servers.
     */
    public OOSManager(Supervisor orb, Mirror mirror, String servicePattern) {
        this.orb = orb;
        this.mirror = mirror;
        this.servicePattern = servicePattern;

        disabled = (servicePattern == null || servicePattern.isEmpty());
        ready = disabled;

        if (!disabled) {
            task = orb.transport().createTask(this);
            task.scheduleNow();
        }
    }

    /**
     * Method invoked when this object is run as a task. This method will update the oos information held by this
     * object.
     */
    public void run() {
        boolean changed = updateFromSlobrok();
        boolean allOk = mirror.ready();
        for (OOSClient client : clients.values()) {
            if (client.isChanged()) {
                changed = true;
            }
            if (!client.isReady()) {
                allOk = false;
            }
        }
        if (changed) {
            Set<String> oos = new LinkedHashSet<String>();
            for (OOSClient client : clients.values()) {
                client.dumpState(oos);
            }
            oosSet = oos;
        }
        if (allOk && !ready) {
            ready = true;
        }
        task.schedule(ready ? 1.0 : 0.1);
    }

    /**
     * This method will check the local slobrok mirror to make sure that its clients are connected to the appropriate
     * services. If anything changes this method returns true.
     *
     * @return True if anything changed.
     */
    private boolean updateFromSlobrok() {
        if (slobrokGen == mirror.updates()) {
            return false;
        }
        slobrokGen = mirror.updates();
        List<Mirror.Entry> newServices = Arrays.asList(mirror.lookup(servicePattern));
        Collections.sort(newServices, new Comparator<Mirror.Entry>() {
            public int compare(Mirror.Entry lhs, Mirror.Entry rhs) {
                return lhs.compareTo(rhs);
            }
        });
        if (newServices.equals(services)) {
            return false;
        }
        Map<String, OOSClient> newClients = new HashMap<String, OOSClient>();
        for (Mirror.Entry service : newServices) {
            OOSClient client = clients.remove(service.getSpec());
            if (client == null) {
                client = new OOSClient(orb, new Spec(service.getSpec()));
            }
            newClients.put(service.getSpec(), client);
        }
        for (OOSClient client : clients.values()) {
            client.shutdown();
        }
        services = newServices;
        clients = newClients;
        return true;
    }

    /**
     * Returns whether or not some initial state has been returned.
     *
     * @return True, if initial state has been found.
     */
    public boolean isReady() {
        return ready;
    }

    /**
     * Returns whether or not the given service has been marked as out of service.
     *
     * @param service The service to check.
     * @return True if the service is out of service.
     */
    @SuppressWarnings({ "RedundantIfStatement" })
    public boolean isOOS(String service) {
        if (disabled) {
            return false;
        }
        Set<String> s = oosSet;
        if (s == null) {
            return false;
        }
        if (!s.contains(service)) {
            return false;
        }
        return true;
    }
}
