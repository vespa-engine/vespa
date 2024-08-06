// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigInstanceSerializer;
import com.yahoo.slime.Slime;
import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.distribution.Group;
import com.yahoo.vdslib.distribution.GroupVisitor;
import com.yahoo.vespa.config.content.StorDistributionConfig;

import java.util.Objects;

/**
 * Immutable encapsulation of a content cluster distribution configuration,
 * including the original config object as well as its processed Distribution
 * and Slime config representations.
 */
public class DistributionConfigBundle {

    // Note: All the encapsulated distribution representations are for the _same_ config
    // (enforcing this invariant is also why this is not a record type).
    private final StorDistributionConfig config;
    // Config instances use reflection for most iterating operations, so cache the underlying
    // string representation to avoid this for such cases.
    private final String canonicalStringRepr;
    private final Slime precomputedSlimeRepr;
    private final Distribution distribution;
    private final int groupsTotal;
    private final int nodesTotal;

    public DistributionConfigBundle(StorDistributionConfig config) {
        Objects.requireNonNull(config);
        this.config = config;
        this.canonicalStringRepr = config.toString();
        precomputedSlimeRepr = new Slime();
        ConfigInstance.serialize(config, new ConfigInstanceSerializer(precomputedSlimeRepr, precomputedSlimeRepr.setObject()));
        this.distribution = new Distribution(config);

        var gv = new GroupVisitor() {
            int groupsTotal = 0;
            int nodesTotal  = 0;

            @Override
            public boolean visitGroup(Group g) {
                if (g.isLeafGroup()) {
                    groupsTotal++;
                    nodesTotal += g.getNodes().size();
                }
                return true;
            }
        };
        this.distribution.visitGroups(gv);
        this.groupsTotal = gv.groupsTotal;
        this.nodesTotal  = gv.nodesTotal;
    }

    public static DistributionConfigBundle of(StorDistributionConfig config) {
        return new DistributionConfigBundle(config);
    }

    public StorDistributionConfig config() { return config; }
    public Slime precomputedSlimeRepr() { return precomputedSlimeRepr; }
    public Distribution distribution() { return distribution; }
    public int totalLeafGroupCount() { return groupsTotal; }
    public int totalNodeCount() { return nodesTotal; }
    public int redundancy() { return config.redundancy(); }
    public int searchableCopies() { return config.ready_copies(); }

    public String highLevelDescription() {
        return "%d nodes; %d groups; redundancy %d; searchable-copies %d".formatted(
                totalNodeCount(), totalLeafGroupCount(), redundancy(), searchableCopies());
    }

    // Note: since all fields are deterministically derived from the original config,
    // we use the canonical string representation for equals, hashCode and toString.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DistributionConfigBundle that = (DistributionConfigBundle) o;
        return Objects.equals(canonicalStringRepr, that.canonicalStringRepr);
    }

    @Override
    public int hashCode() {
        return canonicalStringRepr.hashCode();
    }

    @Override
    public String toString() {
        return canonicalStringRepr;
    }

}
