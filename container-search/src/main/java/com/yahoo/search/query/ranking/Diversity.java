// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * <p>The diversity settings during match phase of a query.
 * These are the same settings for diversity during match phase that can be set in a rank profile
 * and is used for achieving guaranteed diversity at the cost of slightly higher cost as more hits must be
 * considered compared to plain match-phase.</p>
 *
 * <p>You specify an additional attribute to be the diversifier and also min diversity needed.</p>
 *
 * @author baldersheim
 */
public class Diversity implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String ATTRIBUTE = "attribute";
    public static final String MINGROUPS = "minGroups";
    public static final String CUTOFF = "cutoff";
    public static final String FACTOR = "factor";
    public static final String STRATEGY = "strategy";


    static {
        argumentType = new QueryProfileType(Ranking.DIVERSITY);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(ATTRIBUTE, "string"));
        argumentType.addField(new FieldDescription(MINGROUPS, "long"));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    public enum CutoffStrategy {loose, strict};
    private String attribute = null;
    private Long minGroups = null;
    private Double cutoffFactor = null;
    private CutoffStrategy cutoffStrategy= null;

    /**
     * Sets the attribute field which will be used to guarantee diversity.
     * Set to null (default) to disable diversification.
     * <p>
     * If this is set, make sure to also set the maxGroups value.
     * <p>
     * This attribute must be singlevalue.
     */
    public void setAttribute(String attribute) { this.attribute = attribute; }

    /** Returns the attribute to use for diversity, or null if none */
    public String getAttribute() { return attribute; }

    /**
     * Sets the max hits to aim for producing in the match phase.
     * This must be set if an attribute value is set.
     * It should be set to a reasonable fraction of the total documents on each partition.
     */
    public void setMinGroups(long minGroups) { this.minGroups = minGroups; }

    /** Returns the max hits to aim for producing in the match phase on each content node, or null if not set */
    public Long getMinGroups() { return minGroups; }

    public void setCutoffFactor(double cutoffFactor) { this.cutoffFactor = cutoffFactor; }
    public Double getCutoffFactor() { return cutoffFactor; }
    public void setCutoffStrategy(String cutoffStrategy) { this.cutoffStrategy = CutoffStrategy.valueOf(cutoffStrategy); }
    public CutoffStrategy getCutoffStrategy() { return cutoffStrategy; }

    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {
        if (attribute == null && minGroups == null) return;

        if (attribute != null && !attribute.isEmpty()) {
            rankProperties.put("vespa.matchphase.diversity.attribute", attribute);
        }
        if (minGroups != null) {
            rankProperties.put("vespa.matchphase.diversity.mingroups", String.valueOf(minGroups));
        }
        if (cutoffFactor != null) {
            rankProperties.put("vespa.matchphase.diversity.cutoff.factor", String.valueOf(cutoffFactor));
        }
        if (cutoffStrategy != null) {
            rankProperties.put("vespa.matchphase.diversity.cutoff.strategy", cutoffStrategy);
        }
    }

    @Override
    public Diversity clone() {
        try {
            return (Diversity)super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

    @Override
    public int hashCode() {
        int hash = 0;
        if (attribute != null) hash += 11 * attribute.hashCode();
        if (minGroups != null) hash += 13 * minGroups.hashCode();
        if (cutoffFactor != null) hash += 17 * cutoffFactor.hashCode();
        if (cutoffStrategy != null) hash += 19 * cutoffStrategy.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Diversity)) return false;

        Diversity other = (Diversity)o;
        if ( ! Objects.equals(this.attribute, other.attribute)) return false;
        if ( ! Objects.equals(this.minGroups, other.minGroups)) return false;
        if ( ! Objects.equals(this.cutoffFactor, other.cutoffFactor)) return false;
        if ( ! Objects.equals(this.cutoffStrategy, other.cutoffStrategy)) return false;
        return true;
    }

}
