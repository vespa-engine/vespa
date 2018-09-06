package com.yahoo.search.dispatch;

import com.yahoo.search.Query;
import com.yahoo.search.dispatch.SearchCluster.Group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoadBalancer {
    // The implementation here is a simplistic least queries in flight + round-robin load balancer
    // TODO: consider the options in com.yahoo.vespa.model.content.TuningDispatch

    private final static Logger log = Logger.getLogger(LoadBalancer.class.getName());

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

    public Optional<Group> getGroupForQuery(Query query) {
        if (!isInternallyDispatchable) {
            return Optional.empty();
        }

        return allocateNextGroup();
    }

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

            int index = needle;
            for (int i = 0; i < scoreboard.size(); i++) {
                GroupSchedule sched = scoreboard.get(index);
                index++;
                if (index >= scoreboard.size()) {
                    index = 0;
                }
                if (sched.group.hasSufficientCoverage() && (bestSchedule == null || sched.compareTo(bestSchedule) < 0)) {
                    bestSchedule = sched;
                }
            }
            needle++;
            if (needle >= scoreboard.size()) {
                needle = 0;
            }
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

    public static class GroupSchedule implements Comparable<GroupSchedule> {
        private final Group group;
        private int score;

        public GroupSchedule(Group group) {
            this.group = group;
            this.score = 0;
        }

        @Override
        public int compareTo(GroupSchedule that) {
            return this.score - that.score;
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
