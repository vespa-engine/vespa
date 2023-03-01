package com.yahoo.config.application.api;

import com.yahoo.config.provision.RegionName;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Defines the BCP structure for an instance in a deployment spec:
 * A list of region groups where each group contains a set of regions
 * which will handle the traffic of a member in the group when it becomes unreachable.
 *
 * This is used to make bcp-aware autoscaling decisions. If no explicit BCP spec
 * is provided, it is assumed that a regions traffic will be divided equally over all
 * the other regions when it becomes unreachable - i.e a single BCP group is implicitly
 * defined having all defined production regions as members with fraction 1.0.
 *
 * It is assumed that the traffic of the unreachable region is distributed
 * evenly to the other members of the group.
 *
 * A region can be a fractional member of a group, in which case it is assumed that
 * region will only handle that fraction of its share of the unreachable regions traffic,
 * and symmetrically that the other members of the group will only handle that fraction
 * of the fraction regions traffic if it becomes unreachable.
 *
 * Each production region defined in the instance must have fractional memberships in groups that sums to exactly one.
 *
 * If a group has one member it will not set aside any capacity for BCP.
 * If a group has more than two members, the system will attempt to provision capacity
 * for BCP also when a region is unreachable. That is, if there are three member regions, A, B and C,
 * each handling 100 qps, then they each aim to handle 150 in case one goes down. If C goes down,
 * A and B will now handle 150 each, but will each aim to handle 300 each in case the other goes down.
 *
 * @author bratseth
 */
public class Bcp {

    private static final Bcp empty = new Bcp(List.of());

    private final List<Group> groups;

    public Bcp(List<Group> groups) {
        totalMembershipSumsToOne(groups);
        this.groups = List.copyOf(groups);
    }

    public List<Group> groups() { return groups; }

    /** Returns the set of regions declared in the groups of this. */
    public Set<RegionName> regions() {
        return groups.stream().flatMap(group -> group.members().stream()).map(member -> member.region()).collect(Collectors.toSet());
    }

    public boolean isEmpty() { return groups.isEmpty(); }

    /** Returns this bcp spec, or if it is empty, the given bcp spec. */
    public Bcp orElse(Bcp other) {
        return this.isEmpty() ? other : this;
    }

    private void totalMembershipSumsToOne(List<Group> groups) {
        Map<RegionName, Double> totalMembership = new HashMap<>();
        for (var group : groups) {
            for (var member : group.members())
                totalMembership.compute(member.region(), (__, fraction) -> fraction == null ? member.fraction()
                                                                                            : fraction + member.fraction());
        }
        for (var entry : totalMembership.entrySet()) {
            if (entry.getValue() != 1.0)
                throw new IllegalArgumentException("Illegal BCP spec: All regions must have total membership fractions summing to 1.0, but " +
                                                   entry.getKey() + " sums to " + entry.getValue());
        }
    }

    public static Bcp empty() { return empty; }

    @Override
    public String toString() {
        if (isEmpty()) return "empty BCP";
        return "BCP of " + groups;
    }

    public static class Group {

        private final List<RegionMember> members;
        private final Set<RegionName> memberRegions;
        private final Duration deadline;

        public Group(List<RegionMember> members, Duration deadline) {
            this.members = List.copyOf(members);
            this.memberRegions = members.stream().map(member -> member.region()).collect(Collectors.toSet());
            this.deadline = deadline;
        }

        public List<RegionMember> members() { return members; }

        public Set<RegionName> memberRegions() { return memberRegions; }

        /**
         * Returns the max time until the other regions must be able to handle the additional traffic
         * when a region becomes unreachable, which by default is Duration.ZERO.
         */
        public Duration deadline() { return deadline; }

        @Override
        public String toString() {
            return "BCP group of " + members;
        }

    }

    public record RegionMember(RegionName region, double fraction) {

        public RegionMember {
            if (fraction < 0 || fraction > 1)
                throw new IllegalArgumentException("Fraction must be a number between 0.0 and 1.0, but got " + fraction);
        }


    }

}
