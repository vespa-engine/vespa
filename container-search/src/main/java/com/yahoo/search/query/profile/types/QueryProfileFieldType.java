// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;

/**
 * Represents a query profile field type which is a reference to a query profile.
 * The reference may optionally specify the type of the referred query profile.
 *
 * @author bratseth
 */
public class QueryProfileFieldType extends FieldType {

    private final QueryProfileType type;

    public static QueryProfileFieldType fromString(String queryProfileName, QueryProfileTypeRegistry registry) {
        if (queryProfileName==null || queryProfileName.equals(""))
            return new QueryProfileFieldType(null);

        if (registry==null)
            throw new IllegalArgumentException("Can not resolve query profile type '" + queryProfileName +
                                               "' because no registry is provided");
        QueryProfileType queryProfileType=registry.getComponent(queryProfileName);
        if (queryProfileType==null)
            throw new IllegalArgumentException("Could not resolve query profile type '" + queryProfileName + "'");
        return new QueryProfileFieldType(registry.getComponent(queryProfileName));
    }

    public QueryProfileFieldType() { this(null); }

    public QueryProfileFieldType(QueryProfileType type) {
        this.type = type;
    }

    /** Returns the query profile type of this, or null if any type works */
    public QueryProfileType getQueryProfileType() { return type; }

    @Override
    public Class<?> getValueClass() { return QueryProfile.class; }

    @Override
    public String stringValue() {
        return "query-profile" + (type!=null ? ":" + type.getId().getName() : "");
    }

    @Override
    public String toString() {
        return "field type " + stringValue();
    }

    @Override
    public String toInstanceDescription() {
        return "reference to a query profile" + (type!=null ? " of type '" + type.getId().getName() + "'" : "");
    }

    @Override
    public CompiledQueryProfile convertFrom(Object object, ConversionContext context) {
        String profileId = object.toString();
        if (profileId.startsWith("ref:"))
            profileId = profileId.substring("ref:".length());
        CompiledQueryProfile profile = context.registry().getComponent(profileId);
        if (profile == null) return null;
        if (type != null && ! type.equals(profile.getType())) return null;
        return profile;
    }

    @Override
    public QueryProfile convertFrom(Object object, QueryProfileRegistry registry) {
        QueryProfile profile;
        if (object instanceof String)
            profile = registry.getComponent((String)object);
        else if (object instanceof QueryProfile)
            profile = (QueryProfile)object;
        else
            return null;

        // Verify its type as well
        if (type!=null && type!=profile.getType()) return null;
        return profile;
    }

    @Override
    public int hashCode() {
        if (type == null) return 17;
        return type.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof QueryProfileFieldType)) return false;
        QueryProfileFieldType other = (QueryProfileFieldType)o;
        return equals(this.type.getId(), other.type.getId());
    }

    private boolean equals(Object o1, Object o2) {
        if (o1 == null) return o2 == null;
        return o1.equals(o2);
    }

}
