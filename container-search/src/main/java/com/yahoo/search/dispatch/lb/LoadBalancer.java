// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.lb;

import com.yahoo.search.dispatch.RequestDuration;
import com.yahoo.search.dispatch.searchcluster.Group;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * LoadBalancer determines which group of content nodes should be accessed next for each search query when the
 * internal java dispatcher is used.
 * The implementation here is a simplistic least queries in flight + round-robin load balancer
 *
 * @author Olli Virtanen
 */
public class LoadBalancer {

    private static final Logger log = Logger.getLogger(LoadBalancer.class.getName());

    static final double MIN_QUERY_TIME = Duration.ofMillis(1).toMillis()/1000.0;

    private final Map<Integer, GroupStatus> scoreboard;
    private final GroupScheduler scheduler;

    /**
     * The groups which are not in the same availability zone as this container,
     * or empty if all groups are, or if no group is (in which case there is nothing to prefer).
     */
    private final Set<Integer> nonLocalGroups;

    public enum Policy { ROUNDROBIN, ADAPTIVE, BEST_OF_RANDOM_2, LATENCY_AMORTIZED_OVER_TIME}

    public LoadBalancer(Collection<Group> groups, Policy policy, String localAvailabilityZone) {
        this(groups, policy, localAvailabilityZone, System.currentTimeMillis());
    }

    LoadBalancer(Collection<Group> groups, Policy policy, String localAvailabilityZone, long seed) {
        var scoreboard = new HashMap<Integer, GroupStatus>();
        for (Group group : groups)
            scoreboard.put(group.id(), new GroupStatus(group));
        this.scoreboard = Collections.unmodifiableMap(scoreboard);

        if (scoreboard.size() == 1)
            policy = Policy.ROUNDROBIN;

        this.scheduler = switch (policy) {
            case ROUNDROBIN -> new RoundRobinScheduler(scoreboard);
            case BEST_OF_RANDOM_2 -> new BestOfRandom2Scheduler(new Random(), scoreboard);
            case ADAPTIVE -> new AdaptiveScheduler(AdaptiveScheduler.Type.REQUESTS, new Random(seed), scoreboard);
            case LATENCY_AMORTIZED_OVER_TIME -> new AdaptiveScheduler(AdaptiveScheduler.Type.TIME, new Random(), scoreboard);
        };

        Set<Integer> nonLocalGroups = groups.stream()
                                            .filter(group -> ! group.availabilityZone().equals(localAvailabilityZone))
                                            .map(Group::id)
                                            .collect(Collectors.toSet());
        this.nonLocalGroups = nonLocalGroups.size() == groups.size() ? Set.of() : Set.copyOf(nonLocalGroups);
    }

    /**
     * Selects and allocates the search cluster group which is to be used for the next search query.
     * Groups in the same availability zone as this container are preferred when any of them
     * has sufficient coverage.
     * Callers <b>must</b> call {@link #releaseGroup} symmetrically for each taken allocation.
     *
     * @param rejectedGroups if not null, the load balancer will only return groups with IDs not in the set
     * @return the node group to target, or <i>empty</i> if the internal dispatch logic cannot be used
     */
    public Optional<Group> takeAnyGroupNotIn(Set<Integer> rejectedGroups) {
        synchronized (this) {
            Optional<GroupStatus> best = takePreferablyLocalGroup(rejectedGroups);
            if (best.isPresent()) {
                GroupStatus status = best.get();
                status.allocate();
                return Optional.of(status.group);
            } else {
                return Optional.empty();
            }
        }
    }

    private Optional<GroupStatus> takePreferablyLocalGroup(Set<Integer> rejectedGroups) {
        if (! aRemoteIsPreferable(rejectedGroups)) {
            Set<Integer> rejectedOrNonLocal = new HashSet<>(nonLocalGroups);
            if (rejectedGroups != null)
                rejectedOrNonLocal.addAll(rejectedGroups);
            Optional<GroupStatus> local = scheduler.takeNextGroup(rejectedOrNonLocal);
            if (local.isPresent()) return local;
            return scheduler.takeNextGroup(rejectedGroups);
        }
        else {
            return scheduler.takeNextGroup(rejectedGroups);
        }
    }

    /**
     * Returns true if there exists some remote group which is preferable
     * to all the local groups (disregarding rejected).
     */
    private boolean aRemoteIsPreferable(Set<Integer> rejectedGroups) {
        Set<Integer> localGroups = new HashSet<>(scoreboard.keySet());
        if (localGroups.size() == nonLocalGroups.size()) return false; // TODO: Remove when removing the "OR" part of this
        localGroups.removeAll(nonLocalGroups);
        for (var local : localGroups) {
            if (rejectedGroups.contains(local)) continue;
            if (! remoteIsPreferableTo(local, rejectedGroups))
                return false;
        }
        return true;
    }

    private boolean remoteIsPreferableTo(Integer local, Set<Integer> rejectedGroups) {
        for (var remote : nonLocalGroups) {
            if (rejectedGroups.contains(remote)) continue;
            if (scoreboard.get(remote).group.isPreferableTo(scoreboard.get(local).group))
                return true;
        }
        return false;
    }

    /**
     * Allocates a specific group, if present.
     * Callers <b>must</b> call {@link #releaseGroup} symmetrically for each taken allocation.
     */
    public Optional<Group> takeGroup(Group group) {
        synchronized (this) {
            GroupStatus groupStatus = scoreboard.get(group.id());
            if (groupStatus == null) return Optional.empty();
            groupStatus.allocate();
            return Optional.of(group);
        }
    }

    /**
     * Release an allocation given by {@link #takeGroup}. The release must be done exactly once for each allocation.
     *
     * @param group previously allocated group
     * @param success was the query successful
     * @param searchTime query execution time, used for adaptive load balancing
     */
    public void releaseGroup(Group group, boolean success, RequestDuration searchTime) {
        synchronized (this) {
            GroupStatus scheduled = scoreboard.get(group.id());
            scheduled.release(success, searchTime);
        }
    }

    static class GroupStatus {

        interface Decayer {
            void decay(RequestDuration duration);
            double averageCost();
        }

        static class NoDecay implements Decayer {
            public void decay(RequestDuration duration) {}
            public double averageCost() { return MIN_QUERY_TIME; }
        }

        private final Group group;
        private int allocations = 0;
        private Decayer decayer;

        GroupStatus(Group group) {
            this.group = group;
            this.decayer = new NoDecay();
        }

        public Group group() { return group; }

        /** Returns the current number of requests allocated to this. */
        public int allocations() { return allocations; }

        void setDecayer(Decayer decayer) {
            this.decayer = decayer;
        }

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
        public String toString() { return "status of " + group; }

    }

}
