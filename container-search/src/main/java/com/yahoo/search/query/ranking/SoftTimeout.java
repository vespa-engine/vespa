// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Settings for the soft-timeout feature.
 *
 * @author baldersheim
 */
public class SoftTimeout implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String ENABLE = "enable";
    public static final String FACTOR = "factor";
    public static final String TAILCOST = "tailcost";

    /** The full property name for turning softtimeout on or off */
    public static final CompoundName enableProperty = CompoundName.from(Ranking.RANKING + "." + Ranking.SOFTTIMEOUT + "." + ENABLE);

    static {
        argumentType = new QueryProfileType(Ranking.SOFTTIMEOUT);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(ENABLE, "boolean"));
        argumentType.addField(new FieldDescription(FACTOR, "double"));
        argumentType.addField(new FieldDescription(TAILCOST, "double"));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    private Boolean enabled = null;
    private Double factor = null;
    private Double tailcost = null;

    public void setEnable(boolean enable) {
        this.enabled = enable;
    }

    /** Returns whether softtimeout is enabled. Defauyt is true. */
    public Boolean getEnable() {
        if (enabled == null) return Boolean.TRUE;
        return enabled;
    }

    /** Override the adaptive factor determined on the content nodes */
    public void setFactor(double factor) {
        if ((factor < 0.0) || (factor > 1.0)) {
            throw new IllegalInputException("factor must be in the range [0.0, 1.0], got " + factor);
        }
        this.factor = factor;
    }

    public Double getFactor() { return factor; }

    /** Override the tail cost factor determined on the content nodes */
    public void setTailcost(double tailcost) {
        if ((tailcost < 0.0) || (tailcost > 1.0)) {
            throw new IllegalInputException("tailcost must be in the range [0.0, 1.0], got " + tailcost);
        }
        this.tailcost = tailcost;
    }

    public Double getTailcost() { return tailcost; }

    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {
        if (enabled != null)
            rankProperties.put("vespa.softtimeout.enable", String.valueOf(enabled));
        if (factor != null)
            rankProperties.put("vespa.softtimeout.factor", String.valueOf(factor));
        if (tailcost != null)
            rankProperties.put("vespa.softtimeout.tailcost", String.valueOf(tailcost));
    }

    @Override
    public SoftTimeout clone() {
        try {
            return (SoftTimeout) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

    @Override
    public int hashCode() {
        int hash = 0;
        if (enabled != null) hash += 11;
        if (factor != null) hash += 13 * factor.hashCode();
        if (tailcost != null) hash += 17 * tailcost.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof SoftTimeout)) return false;

        SoftTimeout other = (SoftTimeout)o;
        if ( ! Objects.equals(this.enabled, other.enabled)) return false;
        if ( ! Objects.equals(this.factor, other.factor)) return false;
        if ( ! Objects.equals(this.tailcost, other.tailcost)) return false;
        return true;
    }

}
