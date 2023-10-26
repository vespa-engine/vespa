// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.jrt.Spec;
import com.yahoo.jrt.Supervisor;
import com.yahoo.concurrent.SystemTimer;
import com.yahoo.concurrent.Timer;

import java.io.Closeable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Class used to reuse targets for the same address when sending messages over the rpc network.
 *
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 */
public class RPCTargetPool {

    private final Map<Spec, Entry> targets = new HashMap<>();
    private final Timer timer;
    private final long expireMillis;
    private final int numTargetsPerSpec;

    /**
     * Constructs a new instance of this class, and registers the {@link SystemTimer} for detecting and closing
     * connections that have expired according to the given parameter.
     *
     * @param expireSecs The number of seconds until an idle connection is closed.
     */
    public RPCTargetPool(double expireSecs, int numTargetsPerSpec) {
        this(SystemTimer.INSTANCE, expireSecs, numTargetsPerSpec);
    }

    /**
     * Constructs a new instance of this class, using the given {@link Timer} for detecting and closing connections that
     * have expired according to the second paramter.
     *
     * @param timer      The timer to use for connection expiration.
     * @param expireSecs The number of seconds until an idle connection is closed.
     */
    public RPCTargetPool(Timer timer, double expireSecs, int numTargetsPerSpec) {
        this.timer = timer;
        this.expireMillis = (long)(expireSecs * 1000);
        this.numTargetsPerSpec = numTargetsPerSpec;
    }

    /**
     * Closes all unused target connections. Unless the force argument is true, this method will allow a grace period
     * for all connections after last use before it starts closing them. This allows the most recently used connections
     * to stay open.
     *
     * @param force Whether or not to force flush.
     */
    public synchronized void flushTargets(boolean force) {
        Iterator<Entry> it = targets.values().iterator();
        long currentTime = timer.milliTime();
        long expireTime = currentTime - expireMillis;
        while (it.hasNext()) {
            Entry entry = it.next();
            if (entry.isValid()) {
                if (entry.getRefCount() > 1) {
                    entry.lastUse = currentTime;
                    continue; // someone is using this
                }
                if (!force) {
                    if (entry.lastUse > expireTime) {
                        continue; // not sufficiently idle
                    }
                }
            }
            entry.close();
            it.remove();
        }
    }

    /**
     * This method will return a target for the given address. If a target does not currently exist for the given
     * address, it will be created and added to the internal map. Each target is also reference counted so that an
     * active target is never expired.
     *
     * @param orb     The supervisor to use to connect to the target.
     * @param address The address to resolve to a target.
     * @return A target for the given address.
     */
    public RPCTarget getTarget(Supervisor orb, RPCServiceAddress address) {
        Spec spec = address.getConnectionSpec();
        long now = timer.milliTime();
        synchronized (this) {
            Entry entry = targets.get(spec);
            if (entry != null) {
                RPCTarget target = entry.getTarget(now);
                if (target != null) {
                    return target;
                }
                dropTarget(entry, spec);
            }
            return createAndAddTarget(orb, spec, now);
        }
    }

    private void dropTarget(Entry entry, Spec key) {
        entry.close();
        targets.remove(key);
    }

    private RPCTarget createAndAddTarget(Supervisor orb, Spec spec,  long now) {
        RPCTarget [] tmpTargets = new RPCTarget[numTargetsPerSpec];
        for (int i=0; i < tmpTargets.length; i++) {
            tmpTargets[i] = new RPCTarget(spec, orb);
        }
        Entry entry = new Entry(tmpTargets, now);
        targets.put(spec, entry);
        return entry.getTarget(now);
    }


    /**
     * Returns the number of targets currently contained in this.
     *
     * @return The size of the internal map.
     */
    public synchronized int size() {
        return targets.size();
    }

    /**
     * Implements a helper class holds the necessary reference and timestamp of a target. The lastUse member is updated
     * when a call to {@link RPCTargetPool#flushTargets(boolean)} iterates over an active target.
     */
    private static class Entry implements Closeable {

        private final RPCTarget [] targets;
        private int index;
        long lastUse;

        Entry(RPCTarget [] targets, long lastUse) {
            this.targets = targets;
            this.lastUse = lastUse;
        }
        RPCTarget getTarget(long now) {
            if (index >= targets.length) {
                index = 0;
            }
            RPCTarget target = targets[index];
            if (target.getJRTTarget().isValid()) {
                target.addRef();
                lastUse = now;
                index++;
                return target;
            }
            return null;
        }
        boolean isValid() {
            for (RPCTarget target : targets) {
                if ( ! target.getJRTTarget().isValid()) {
                    return false;
                }
            }
            return true;
        }

        int getRefCount() {
            int refCount = 0;
            for (RPCTarget target : targets) {
                refCount += target.getRefCount();
            }
            return refCount;
        }

        @Override
        public void close() {
            for (RPCTarget target : targets) {
                target.subRef();
            }
        }
    }
}
