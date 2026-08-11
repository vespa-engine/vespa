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
import java.util.stream.Collectors;

/**
 * LoadBalancer determines which group of content nodes should be accessed next for each search query when the
 * internal java dispatcher is used.
 * The implementation here is a simplistic least queries in flight + round-robin load balancer.
 *
 * @author Olli Virtanen
 */
public class LoadBalancer {

    static final double MIN_QUERY_TIME = Duration.ofMillis(1).toMillis()/1000.0;

    private final Map<Integer, TrackedGroup> scoreboard;
    private final GroupScheduler scheduler;

    /**
     * The groups which are not in the same availability zone as this container,
     * or empty if all groups are, or if no group is (in which case there is nothing to prefer).
     */
    private final Set<Integer> remoteGroups;

    public enum Policy { ROUNDROBIN, ADAPTIVE, BEST_OF_RANDOM_2, LATENCY_AMORTIZED_OVER_TIME}

    public LoadBalancer(Collection<Group> groups, Policy policy, String localAvailabilityZone) {
        this(groups, policy, localAvailabilityZone, System.currentTimeMillis());
    }

    LoadBalancer(Collection<Group> groups, Policy policy, String localAvailabilityZone, long seed) {
        var scoreboard = new HashMap<Integer, TrackedGroup>();
        for (Group group : groups)
            scoreboard.put(group.id(), new TrackedGroup(group));
        this.scoreboard = Collections.unmodifiableMap(scoreboard);

        if (scoreboard.size() == 1)
            policy = Policy.ROUNDROBIN;

        this.scheduler = switch (policy) {
            case ROUNDROBIN -> new RoundRobinScheduler(scoreboard);
            case BEST_OF_RANDOM_2 -> new BestOfRandom2Scheduler(new Random(), scoreboard);
            case ADAPTIVE -> new AdaptiveScheduler(AdaptiveScheduler.Type.REQUESTS, new Random(seed), scoreboard);
            case LATENCY_AMORTIZED_OVER_TIME -> new AdaptiveScheduler(AdaptiveScheduler.Type.TIME, new Random(), scoreboard);
        };

        this.remoteGroups = groups.stream()
                                  .filter(group -> ! group.availabilityZone().equals(localAvailabilityZone))
                                  .map(Group::id)
                                  .collect(Collectors.toUnmodifiableSet());
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
            Optional<TrackedGroup> best = takePreferablyLocalGroup(rejectedGroups);
            if (best.isPresent()) {
                TrackedGroup group = best.get();
                group.allocate();
                return Optional.of(group.group());
            } else {
                return Optional.empty();
            }
        }
    }

    private Optional<TrackedGroup> takePreferablyLocalGroup(Set<Integer> rejectedGroups) {
        if (! aRemoteIsPreferable(rejectedGroups)) {
            Set<Integer> rejectedOrNonLocal = new HashSet<>(remoteGroups);
            if (rejectedGroups != null)
                rejectedOrNonLocal.addAll(rejectedGroups);
            Optional<TrackedGroup> local = scheduler.takeNextGroup(rejectedOrNonLocal);
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
        localGroups.removeAll(remoteGroups);
        for (var local : localGroups) {
            if (rejectedGroups.contains(local)) continue;
            if (! remoteIsPreferableTo(local, rejectedGroups))
                return false;
        }
        return true;
    }

    private boolean remoteIsPreferableTo(Integer local, Set<Integer> rejectedGroups) {
        for (var remote : remoteGroups) {
            if (rejectedGroups.contains(remote)) continue;
            if (scoreboard.get(remote).group().isPreferableTo(scoreboard.get(local).group()))
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
            TrackedGroup groupStatus = scoreboard.get(group.id());
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
            TrackedGroup scheduled = scoreboard.get(group.id());
            scheduled.release(success, searchTime);
        }
    }

}
