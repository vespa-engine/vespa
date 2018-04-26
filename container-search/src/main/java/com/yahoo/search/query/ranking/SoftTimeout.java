// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Holds the settings for the soft-timeout feature.
 *
 * @author baldersheim
 */
public class SoftTimeout implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String ENABLE = "enable";
    public static final String FACTOR = "factor";
    public static final String TAILCOST = "tailcost";

    static {
        argumentType = new QueryProfileType(Ranking.SOFTTIMEOUT);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(TAILCOST, "double"));
        argumentType.addField(new FieldDescription(ENABLE, "boolean"));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    public Boolean enable = null;
    private Double factor = null;
    private Double tailcost = null;

    public void setEnable(boolean enable) { this.enable = enable; }

    public Boolean getEnable() { return enable; }

    public void setFactor(double factor) {
        if ((factor < 0.0) || (factor > 1.0)) {
            throw new IllegalArgumentException("factor must be in the range [0.0, 1.0]. It is " + factor);
        }
        this.factor = factor;
    }
    public Double getFactor() { return factor; }
    public void setTailcost(double tailcost) {
        if ((tailcost < 0.0) || (tailcost > 1.0)) {
            throw new IllegalArgumentException("tailcost must be in the range [0.0, 1.0]. It is " + tailcost);
        }
        this.tailcost = tailcost;
    }
    public Double getTailcost() { return tailcost; }

    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {

        if (enable != null) {
            rankProperties.put("vespa.softtimeout.enable", String.valueOf(enable));
        }
        if (factor != null) {
            rankProperties.put("vespa.softtimeout.factor", String.valueOf(factor));
        }
        if (tailcost != null) {
            rankProperties.put("vespa.softtimeout.tailcost", String.valueOf(tailcost));
        }
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
        if (enable != null) hash += 11 * enable.hashCode();
        if (factor != null) hash += 13 * factor.hashCode();
        if (tailcost != null) hash += 17 * tailcost.hashCode();
        return hash;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof SoftTimeout)) return false;

        SoftTimeout other = (SoftTimeout)o;
        if ( ! Objects.equals(this.enable, other.enable)) return false;
        if ( ! Objects.equals(this.factor, other.factor)) return false;
        if ( ! Objects.equals(this.tailcost, other.tailcost)) return false;
        return true;
    }

}
