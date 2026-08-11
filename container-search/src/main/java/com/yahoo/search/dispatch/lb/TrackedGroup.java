// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.lb;

import com.yahoo.search.dispatch.RequestDuration;
import com.yahoo.search.dispatch.searchcluster.Group;

import java.util.logging.Logger;

/**
 * A group with information about
 * - how many request it is currently handling (allocations), and
 * - and how costly it has been for it to execute queries recently
 *   (wrapped in a decayer which supplies the definition of "recently")
 *
 * @author Olli Virtanen
 */
class TrackedGroup {

    private static final Logger log = Logger.getLogger(LoadBalancer.class.getName());

    interface Decayer {
        void decay(RequestDuration duration);

        double averageCost();
    }

    static class NoDecay implements Decayer {
        public void decay(RequestDuration duration) {}

        public double averageCost() {return LoadBalancer.MIN_QUERY_TIME;}
    }

    private final Group group;
    private int allocations = 0;
    private Decayer decayer;

    TrackedGroup(Group group) {
        this.group = group;
        this.decayer = new NoDecay();
    }

    void setDecayer(Decayer decayer) {
        this.decayer = decayer;
    }

    public Group group() {return group;}

    /** Returns the current number of requests allocated to this. */
    public int allocations() {return allocations;}

    void allocate() {
        allocations++;
    }

    void release(boolean success, RequestDuration searchTime) {
        allocations--;
        if (allocations < 0) {
            log.warning("Double free of query target group detected");
            allocations = 0;
        }
        if (success) {
            decayer.decay(searchTime);
        }
    }

    double weight() {
        return 1.0 / decayer.averageCost();
    }

    int groupId() {
        return group.id();
    }

    @Override
    public String toString() {return "status of " + group;}

}
