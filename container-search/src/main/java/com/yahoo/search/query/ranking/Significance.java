// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.ranking;

import com.yahoo.search.query.Ranking;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.FieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.Objects;
import java.util.Optional;

/**
 * The significance ranking settings of this query.
 *
 * @author MariusArhaug
 */
public class Significance implements Cloneable {

    /** The type representing the property arguments consumed by this */
    private static final QueryProfileType argumentType;

    public static final String USE_MODEL = "useModel";

    static {
        argumentType = new QueryProfileType(Ranking.SECOND_PHASE);
        argumentType.setStrict(true);
        argumentType.setBuiltin(true);
        argumentType.addField(new FieldDescription(USE_MODEL, FieldType.booleanType));
        argumentType.freeze();
    }
    public static QueryProfileType getArgumentType() { return argumentType; }

    private Boolean useModel = null;

    public void setUseModel(boolean useModel) {
        this.useModel = useModel;
    }

    public Optional<Boolean> getUseModel() {
        return Optional.ofNullable(useModel);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.useModel);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof Significance other) {
            if ( ! Objects.equals(this.useModel, other.useModel)) return false;
            return true;
        }
        return false;
    }

    @Override
    public Significance clone() {
        try {
            return (Significance) super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Won't happen", e);
        }
    }

}
