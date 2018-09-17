// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.processing.request.CompoundName;
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
    // TODO: consider the options in com.yahoo.vespa.model.content.TuningDispatch

    private static final Logger log = Logger.getLogger(LoadBalancer.class.getName());

    private static final CompoundName QUERY_NODE_GROUP_AFFINITY = new CompoundName("dispatch.group.affinity");

    private final boolean isInternallyDispatchable;
    private final List<GroupSchedule> scoreboard;
    private int needle = 0;

    public LoadBalancer(SearchCluster searchCluster) {
        if (searchCluster == null) {
            this.isInternallyDispatchable = false;
            this.scoreboard = null;
            return;
        }
        this.isInternallyDispatchable = (searchCluster.groupSize() == 1);
        this.scoreboard = new ArrayList<>(searchCluster.groups().size());

        for (Group group : searchCluster.groups().values()) {
            scoreboard.add(new GroupSchedule(group));
        }
        Collections.shuffle(scoreboard);
    }

    /**
     * Select and allocate the search cluster group which is to be used for the provided query. Callers <b>must</b> call
     * {@link #releaseGroup} symmetrically for each taken allocation.
     *
     * @param query the query for which this allocation is made
     * @return The node group to target, or <i>empty</i> if the internal dispatch logic cannot be used
     */
    public Optional<Group> takeGroupForQuery(Query query) {
        if (!isInternallyDispatchable) {
            return Optional.empty();
        }

        Integer groupAffinity = query.properties().getInteger(QUERY_NODE_GROUP_AFFINITY);
        if (groupAffinity != null) {
            Optional<Group> previouslyChosen = allocateFromGroup(groupAffinity);
            if (previouslyChosen.isPresent()) {
                return previouslyChosen;
            }
        }
        Optional<Group> allocatedGroup = allocateNextGroup();
        allocatedGroup.ifPresent(group -> query.properties().set(QUERY_NODE_GROUP_AFFINITY, group.id()));
        return allocatedGroup;
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

    private Optional<Group> allocateFromGroup(int groupId) {
        synchronized (this) {
            for (GroupSchedule schedule : scoreboard) {
                if (schedule.group.id() == groupId) {
                    schedule.adjustScore(1);
                    return Optional.of(schedule.group);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Group> allocateNextGroup() {
        synchronized (this) {
            GroupSchedule bestSchedule = null;

            int index = needle;
            for (int i = 0; i < scoreboard.size(); i++) {
                GroupSchedule sched = scoreboard.get(index);
                if (sched.isPreferredOver(bestSchedule)) {
                    bestSchedule = sched;
                }
                index = nextScoreboardIndex(index);
            }
            needle = nextScoreboardIndex(needle);

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
            if (! group.hasSufficientCoverage()) {
                return false;
            }
            if (other == null) {
                return true;
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
