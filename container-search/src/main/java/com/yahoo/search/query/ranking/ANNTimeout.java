// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.processing.IllegalInputException;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;

/**
 * Settings for the ANN-timeout feature.
 *
 * @author baldersheim
 */
public class ANNTimeout implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String ANNTIMEOUT = "anntimeout";
    public static final String ENABLE = "enable";
    public static final String FACTOR = "factor";

    /** The full property name for turning anntimeout on or off */
    public static final CompoundName enableProperty = CompoundName.fromComponents(Ranking.RANKING, ANNTIMEOUT, ENABLE);

    static {
        argumentType = new QueryProfileType(ANNTIMEOUT);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(ENABLE, "boolean"));
        argumentType.addField(new FieldDescription(FACTOR, "double"));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    private Boolean enabled = null;
    private Double factor = null;

    public void setEnable(boolean enable) {
        this.enabled = enable;
    }

    /** Returns whether anntimeout is enabled. Defauyt is true. */
    public Boolean getEnable() {
        if (enabled == null) return Boolean.TRUE;
        return enabled;
    }

    /** Override the default factor */
    public void setFactor(double factor) {
        if ((factor < 0.0) || (factor > 1.0)) {
            throw new IllegalInputException("factor must be in the range [0.0, 1.0], got " + factor);
        }
        this.factor = factor;
    }

    public Double getFactor() { return factor; }

    /** Internal operation - DO NOT USE */
    public void prepare(RankProperties rankProperties) {
        if (enabled != null)
            rankProperties.put("vespa.anntimeout.enable", String.valueOf(enabled));
        if (factor != null)
            rankProperties.put("vespa.anntimeout.factor", String.valueOf(factor));
    }

    @Override
    public ANNTimeout clone() {
        try {
            return (ANNTimeout) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, factor);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof ANNTimeout other)) return false;

        if ( ! Objects.equals(this.enabled, other.enabled)) return false;
        if ( ! Objects.equals(this.factor, other.factor)) return false;
        return true;
    }

}
