// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.component.ComponentId;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.types.QueryProfileType;

/**
 * A regular query profile which knows it is storing overrides (not configured profiles)
 * and that implements override legality checking.
 *
 * @author bratseth
 */
public class OverridableQueryProfile extends QueryProfile {

    private static final String simpleClassName = OverridableQueryProfile.class.getSimpleName();

    /** Creates an unbacked overridable query profile */
    protected OverridableQueryProfile(QueryProfileRegistry owner) {
        this("", owner);
    }

    protected OverridableQueryProfile(String sourceName, QueryProfileRegistry owner) {
        super(createAnonymousId(owner), sourceName, owner);
    }

    @Override
    protected Object checkAndConvertAssignment(String localName, Object inputValue, QueryProfileRegistry registry) {
        Object value = super.checkAndConvertAssignment(localName, inputValue, registry);
        if (value != null && value.getClass() == QueryProfile.class) { // We are assigning a query profile - make it overridable
            return new BackedOverridableQueryProfile((QueryProfile)value);
        }
        return value;
    }

    @Override
    protected QueryProfile createSubProfile(String name, DimensionBinding binding) {
        // Nothing is set in this branch, so nothing to override, but need override checking
        return new OverridableQueryProfile(getSource(), getOwner());
    }

    /** Returns a clone of this which can be independently overridden */
    @Override
    public OverridableQueryProfile clone() {
        if (isFrozen()) return this;
        OverridableQueryProfile clone = (OverridableQueryProfile)super.clone();
        clone.initId(createAnonymousId(getOwner()));
        return clone;
    }

    @Override
    public String toString() {
        return "an overridable query profile with no backing";
    }

    private static ComponentId createAnonymousId(QueryProfileRegistry owner) {
        return owner != null ? owner.createAnonymousId(simpleClassName)
                             : ComponentId.createAnonymousComponentId(simpleClassName);
    }

}
