// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * The match phase ranking settings of this query.
 * These are the same settings for match phase that can be set in a rank profile
 * and is used for achieving reasonable query behavior given a query which causes too many matches:
 * The engine will fall back to retrieving the best values according to the attribute given here
 * during matching.
 * <p>
 * For this feature to work well, the order given by the attribute should correlate reasonably with the order
 * of results produced if full evaluation is performed.
 *
 * @author bratseth
 */
public class MatchPhase implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String ATTRIBUTE = "attribute";
    public static final String ASCENDING = "ascending";
    public static final String MAX_HITS = "maxHits";
    public static final String MAX_FILTER_COVERAGE = "maxFilterCoverage";

    static {
        argumentType =new QueryProfileType(Ranking.MATCH_PHASE);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(ATTRIBUTE, "string"));
        argumentType.addField(new FieldDescription(ASCENDING, "boolean"));
        argumentType.addField(new FieldDescription(MAX_HITS, "long"));
        argumentType.addField(new FieldDescription(MAX_FILTER_COVERAGE, "double"));
        argumentType.addField(new FieldDescription(Ranking.DIVERSITY, "query-profile", "diversity"));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    private String attribute = null;
    private boolean ascending = false;
    private Long maxHits = null;
    private Double maxFilterCoverage = 0.2;
    private Diversity diversity = new Diversity();

    /**
     * Sets the attribute field which will be used to decide the best matches after it has been determined
     * during matching that this query is going to cause too many matches.
     * Set to null (default) to disable degradation.
     * <p>
     * If this is set, make sure to also set the maxHits value.
     * Otherwise, the attribute setting is ignored.
     * <p>
     * This attribute should have fast-search turned on.
     */
    public void setAttribute(String attribute) { this.attribute = attribute; }

    /** Returns the attribute to use for degradation, or null if none */
    public String getAttribute() { return attribute; }

    /**
     * Set to true to sort by the attribute in ascending order when this is in use during the match phase,
     * false (default) to use descending order.
     */
    public void setAscending(boolean ascending) { this.ascending = ascending; }

    /**
     * Returns the order to sort the attribute during the path phase when this takes effect.
     */
    public boolean getAscending() { return ascending; }

    /**
     * Sets the max hits to aim for producing in the match phase.
     * This must be set if an attribute value is set.
     * It should be set to a reasonable fraction of the total documents on each partition.
     */
    public void setMaxHits(long maxHits) { this.maxHits = maxHits; }

    public void setMaxFilterCoverage(double maxFilterCoverage) {
        if ((maxFilterCoverage < 0.0) || (maxFilterCoverage > 1.0)) {
            throw new IllegalInputException("maxFilterCoverage must be in the range [0.0, 1.0]. It is " + maxFilterCoverage);
        }

        this.maxFilterCoverage = maxFilterCoverage;
    }

    /** Returns the max hits to aim for producing in the match phase on each content node, or null if not set */
    public Long getMaxHits() { return maxHits; }

    public Double getMaxFilterCoverage() { return maxFilterCoverage; }

    public Diversity getDiversity() { return diversity; }

    public void setDiversity(Diversity diversity) {
        this.diversity = diversity;
    }

    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {
        if (attribute == null || maxHits == null) return;

        rankProperties.put("vespa.matchphase.degradation.attribute", attribute);
        if (ascending) { // backend default is descending
            rankProperties.put("vespa.matchphase.degradation.ascendingorder", "true");
        }
        rankProperties.put("vespa.matchphase.degradation.maxhits", String.valueOf(maxHits));
        rankProperties.put("vespa.matchphase.degradation.maxfiltercoverage", String.valueOf(maxFilterCoverage));
        diversity.prepare(rankProperties);
    }

    @Override
    public int hashCode() {
        int hash = 0;
        hash += 13 * Boolean.hashCode(ascending);
        hash += 19 * diversity.hashCode();
        if (attribute != null) hash += 11 * attribute.hashCode();
        if (maxHits != null) hash += 17 * maxHits.hashCode();
        hash += 23 * maxFilterCoverage.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof MatchPhase)) return false;

        MatchPhase other = (MatchPhase)o;
        if ( this.ascending != other.ascending) return false;
        if ( ! Objects.equals(this.attribute, other.attribute)) return false;
        if ( ! Objects.equals(this.maxHits, other.maxHits)) return false;
        if ( ! Objects.equals(this.diversity, other.diversity)) return false;
        if ( ! Objects.equals(this.maxFilterCoverage, other.maxFilterCoverage)) return false;
        return true;
    }

    @Override
    public MatchPhase clone() {
        try {
            MatchPhase clone = (MatchPhase)super.clone();
            clone.diversity = diversity.clone();
            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

}
