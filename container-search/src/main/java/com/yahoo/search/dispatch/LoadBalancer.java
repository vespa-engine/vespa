// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.search.Query;
import com.yahoo.search.dispatch.SearchCluster.Group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * LoadBalancer determines which group of content nodes should be accessed next for each search query when the internal java dispatcher is
 * used.
 *
 * @author ollivir
 */
public class LoadBalancer {
    // The implementation here is a simplistic least queries in flight + round-robin load balancer

    private static final Logger log = Logger.getLogger(LoadBalancer.class.getName());

    private final List<GroupSchedule> scoreboard;
    private int needle = 0;

    public LoadBalancer(SearchCluster searchCluster, boolean roundRobin) {
        if (searchCluster == null) {
            this.scoreboard = null;
            return;
        }
        this.scoreboard = new ArrayList<>(searchCluster.groups().size());

        for (Group group : searchCluster.orderedGroups()) {
            scoreboard.add(new GroupSchedule(group));
        }

        if(! roundRobin) {
            // TODO - More randomness could be desirable
            Collections.shuffle(scoreboard);
        }
    }

    /**
     * Select and allocate the search cluster group which is to be used for the provided query. Callers <b>must</b> call
     * {@link #releaseGroup} symmetrically for each taken allocation.
     *
     * @param query the query for which this allocation is made
     * @return The node group to target, or <i>empty</i> if the internal dispatch logic cannot be used
     */
    public Optional<Group> takeGroupForQuery(Query query) {
        if (scoreboard == null) {
            return Optional.empty();
        }

        return allocateNextGroup();
    }

    /**
     * Release an allocation given by {@link #takeGroupForQuery(Query)}. The release must be done exactly once for each allocation.
     *
     * @param group
     *            previously allocated group
     */
    public void releaseGroup(Group group) {
        synchronized (this) {
            for (GroupSchedule sched : scoreboard) {
                if (sched.group.id() == group.id()) {
                    sched.adjustScore(-1);
                    break;
                }
            }
        }
    }

    private Optional<Group> allocateNextGroup() {
        synchronized (this) {
            GroupSchedule bestSchedule = null;
            int bestIndex = needle;

            int index = needle;
            for (int i = 0; i < scoreboard.size(); i++) {
                GroupSchedule sched = scoreboard.get(index);
                if (sched.isPreferredOver(bestSchedule)) {
                    bestSchedule = sched;
                    bestIndex = index;
                }
                index = nextScoreboardIndex(index);
            }
            needle = nextScoreboardIndex(bestIndex);

            Group ret = null;
            if (bestSchedule != null) {
                bestSchedule.adjustScore(1);
                ret = bestSchedule.group;
            }
            if (log.isLoggable(Level.FINE)) {
                log.fine("Offering <" + ret + "> for query connection");
            }
            return Optional.ofNullable(ret);
        }
    }

    private int nextScoreboardIndex(int current) {
        int next = current + 1;
        if (next >= scoreboard.size()) {
            next %= scoreboard.size();
        }
        return next;
    }

    private static class GroupSchedule {
        private final Group group;
        private int score;

        public GroupSchedule(Group group) {
            this.group = group;
            this.score = 0;
        }

        public boolean isPreferredOver(GroupSchedule other) {
            if (other == null) {
                return true;
            }

            // different coverage
            if (this.group.hasSufficientCoverage() != other.group.hasSufficientCoverage()) {
                if (! this.group.hasSufficientCoverage()) {
                    // this doesn't have coverage, other does
                    return false;
                } else {
                    // other doesn't have coverage, this does
                    return true;
                }
            }

            return this.score < other.score;
        }

        public void adjustScore(int amount) {
            this.score += amount;
            if (score < 0) {
                log.warning("Double free of query target group detected");
                score = 0;
            }
        }
    }
}
