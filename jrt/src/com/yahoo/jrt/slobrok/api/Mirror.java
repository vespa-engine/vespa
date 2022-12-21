// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jrt.slobrok.api;

import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Task;
import com.yahoo.jrt.TransportThread;
import com.yahoo.jrt.Values;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * A Mirror object is used to keep track of the services registered
 * with a slobrok cluster.
 *
 * Updates to the service repository are fetched in the
 * background. Lookups against this object is done using an internal
 * mirror of the service repository.
 */
public class Mirror implements IMirror {

    private static final Logger log = Logger.getLogger(Mirror.class.getName());

    private final Supervisor orb;
    private final SlobrokList slobroks;
    private String currSlobrok;
    private final BackOffPolicy backOff;
    private volatile int updates = 0;
    private volatile long iterations = 0;
    private boolean requestDone = false;
    private boolean logOnSuccess = true;
    private final AtomicReference<Entry[]> specs = new AtomicReference<>(new Entry[0]);
    private int specsGeneration = 0;
    private final TransportThread transportThread;
    private final Task updateTask;
    private final RequestWaiter reqWait;
    private Target target = null;
    private Request req = null;

    /**
     * Create a new MirrorAPI using the given Supervisor and slobrok connect specs.
     *
     * @param orb the Supervisor to use
     * @param slobroks slobrok connect spec list
     * @param bop custom backoff policy, mostly useful for testing
     */
    public Mirror(Supervisor orb, SlobrokList slobroks, BackOffPolicy bop) {
        this.orb = orb;
        this.slobroks = slobroks;
        this.backOff = bop;
        transportThread = orb.transport().selectThread();
        updateTask = transportThread.createTask(this::checkForUpdate);
        reqWait = new RequestWaiter() {
                public void handleRequestDone(Request req) {
                    requestDone = true;
                    updateTask.scheduleNow();
                    transportThread.wakeup_if_not_self();
                }
            };
        startFetchRequest();
    }

    /**
     * Create a new MirrorAPI using the given Supervisor and slobrok
     * connect specs.
     *
     * @param orb the Supervisor to use
     * @param slobroks slobrok connect spec list
     */
    public Mirror(Supervisor orb, SlobrokList slobroks) {
        this(orb, slobroks, new BackOff());
    }

    /**
     * Shut down the Mirror. This will close any open connections,
     * stop the regular mirror updates, and discard all entries.
     */
    public void shutdown() {
        updateTask.kill();
        transportThread.perform(this::handleShutdown);
    }

    @Override
    public List<Entry> lookup(String pattern) {
        ArrayList<Entry> found = new ArrayList<>();
        char[] p = pattern.toCharArray();
        for (Entry specEntry : specs.get()) {
            if (match(specEntry.getNameArray(), p)) {
                found.add(specEntry);
            }
        }
        return found;
    }

    @Override
    public int updates() {
        return updates;
    }

    /**
     * Ask if the MirrorAPI has got any useful information from the Slobrok.
     *
     * On application startup it is often useful to run the event loop for some time until this functions returns true
     * (or if it never does, time out and tell the user there was no answer from any Service Location Broker).
     *
     * @return true if the MirrorAPI object has asked for updates from a Slobrok and got any answer back
     */
    public boolean ready() {
        return (updates != 0);
    }

    /**
     * Returns whether this mirror is connected to the slobrok server at this
     * time or not.
     */
    public boolean connected() {
        return (target != null);
    }

    /**
     * Match a single name against a pattern.
     * A pattern can contain '*' to match until the next '/' separator,
     * and end with '**' to match the rest of the name.
     * Note that this isn't quite globbing, as there is no backtracking.
     *
     * @param name the name
     * @param pattern the pattern
     * @return true if the name matches the pattern
     */
    static boolean match(char [] name, char [] pattern) {
        int ni = 0;
        int pi = 0;

        while (ni < name.length && pi < pattern.length) {
            if (name[ni] == pattern[pi]) {
                ni++;
                pi++;
            } else if (pattern[pi] == '*') {
                pi++;
                while (ni < name.length && name[ni] != '/') {
                    ni++;
                }
                if (pi < pattern.length && pattern[pi] == '*') {
                    pi++;
                    ni = name.length;
                }
            } else {
                return false;
            }
        }
        while (pi < pattern.length && pattern[pi] == '*') {
            pi++;
        }
        return ((ni == name.length) && (pi == pattern.length));
    }

    /**
     * Invoked by the update task.
     */
    private void checkForUpdate() {
        ++iterations;
        if (requestDone) {
            handleUpdate();
            requestDone = false;
        }
        startFetchRequest();
    }

    private void startFetchRequest() {
        if (target != null && ! slobroks.contains(currSlobrok)) {
            log.log(Level.INFO, "location broker "+currSlobrok+" removed, will disconnect and use one of: "+slobroks);
            target.close();
            target = null;
        }
        if (target == null) {
            logOnSuccess = true;
            currSlobrok = slobroks.nextSlobrokSpec();
            if (currSlobrok == null) {
                double delay = backOff.get();
                Level level = Level.FINE;
                if (backOff.shouldInform(delay)) level = Level.INFO;
                // note: since this happens quite often during normal operation,
                // the level is lower than the more-natural Level.WARNING:
                if (backOff.shouldWarn(delay)) level = Level.INFO;
                log.log(level, "no location brokers available, retrying: "+slobroks+" (in " + delay + " seconds)");
                updateTask.schedule(delay);
                return;
            }
            log.fine(() -> "Try connecting to "+currSlobrok);
            target = orb.connect(new Spec(currSlobrok));
            specsGeneration = 0;
        }
        req = new Request("slobrok.incremental.fetch");
        req.parameters().add(new Int32Value(specsGeneration)); // gencnt
        req.parameters().add(new Int32Value(5000));     // mstimeout
        target.invokeAsync(req, Duration.ofSeconds(40), reqWait);
    }

    private void handleUpdate() {
        if (!req.checkReturnTypes("iSSSi")
            || (req.returnValues().get(2).count() !=
                req.returnValues().get(3).count()))
        {
            if (! logOnSuccess) {
                log.log(Level.INFO, "Error with location broker "+currSlobrok+" update: " + req.errorMessage() +
                        " (error code " + req.errorCode() + ")");
            } else {
                log.fine(() -> "Error with location broker "+currSlobrok+" update: " + req.errorMessage() +
                         " (error code " + req.errorCode() + ")");
            }
            target.close();
            target = null; // try next slobrok
            return;
        }
        Values answer = req.returnValues();

        int diffFromGeneration = answer.get(0).asInt32();
        int diffToGeneration   = answer.get(4).asInt32();
        if (specsGeneration != diffToGeneration) {

            int      nRemoves = answer.get(1).count();
            String[]        r = answer.get(1).asStringArray();

            int      numNames = answer.get(2).count();
            String[]        n = answer.get(2).asStringArray();
            String[]        s = answer.get(3).asStringArray();

            Entry[]  newSpecs;
            if (diffFromGeneration == 0) {
                newSpecs = new Entry[numNames];
                for (int idx = 0; idx < numNames; idx++) {
                    newSpecs[idx] = new Entry(n[idx], s[idx]);
                }
            } else {
                Map<String, Entry> map = new HashMap<>();
                for (Entry e : specs.get()) {
                    map.put(e.getName(), e);
                }
                for (String rem : r) {
                    map.remove(rem);
                }
                for (int idx = 0; idx < numNames; idx++) {
                    map.put(n[idx], new Entry(n[idx], s[idx]));
                }
                newSpecs = new Entry[map.size()];
                int idx = 0;
                for (Entry e : map.values()) {
                    newSpecs[idx++] = e;
                }
            }
            if (logOnSuccess) {
                log.log(Level.INFO, "successfully connected to location broker "+currSlobrok+" (mirror initialized with "+newSpecs.length+" service names)");
                logOnSuccess = false;
            } else {
                log.fine(() -> "successfully updated from location broker "+currSlobrok+" (now "+newSpecs.length+" service names)");
            }
            specs.set(newSpecs);

            specsGeneration = diffToGeneration;
            int u = (updates + 1);
            if (u == 0) {
                u++;
            }
            updates = u;
        } else {
            log.fine(() -> "NOP update from location broker "+currSlobrok+" (curr gen "+specsGeneration+")");
        }
        backOff.reset();
    }

    /**
     * Invoked from the transport thread, requested by the shutdown
     * method.
     */
    private void handleShutdown() {
        if (req != null) {
            req.abort();
            req = null;
        }
        if (target != null) {
            target.close();
            target = null;
        }
        specs.set(new Entry[0]);
    }

    /**
     * An Entry contains the name and connection spec for a single
     * service.
     */
    public static final class Entry implements Comparable<Entry> {

        private final String name;
        private final Spec spec;
        private final char [] nameArray;

        public Entry(String name, String spec) {
            this.name = name;
            this.spec = new Spec(spec);
            this.nameArray = name.toCharArray();
        }

        public boolean equals(Object rhs) {
            if (rhs == null || !(rhs instanceof Entry)) {
                return false;
            }
            Entry e = (Entry) rhs;
            return (name.equals(e.name) && spec.equals(e.spec));
        }

        public int hashCode() {
            return (name.hashCode() + spec.hashCode());
        }

        public int compareTo(Entry b) {
            int diff = name.compareTo(b.name);
            return diff != 0 ? diff : spec.compareTo(b.spec);
        }

        char [] getNameArray() { return nameArray; }
        public String getName() { return name; }
        public Spec getSpec() { return spec; }
        public String getSpecString() { return spec.toString(); }

    }

    public void dumpState() {
        log.log(Level.INFO, "location broker mirror state: " +
                " iterations: " + iterations +
                ", connected to: " + target +
                ", seen " + updates + " updates" +
                ", current server: "+ currSlobrok +
                ", list of servers: " + slobroks);
    }

    public long getIterations() {
        return iterations;
    }

}
