// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.yahoo.processing.request.CompoundName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A wrapper of a query profile where overrides to the values in the referenced
 * profile can be set.
 * This is used to allow configured overrides (in a particular referencing profile) of a referenced query profile.
 * Properties which are defined as not overridable in the type (if any) of the referenced query profile
 * cannot be set.
 *
 * @author bratseth
 */
public class BackedOverridableQueryProfile extends OverridableQueryProfile implements Cloneable {

    /** The backing read only query profile, or null if this is not backed */
    private final QueryProfile backingProfile;

    /**
     * Creates an overridable profile from the given backing profile. The backing profile will never be
     * written to.
     *
     * @param backingProfile the backing profile, which is assumed read only, never null
     */
    public BackedOverridableQueryProfile(QueryProfile backingProfile) {
        super(backingProfile.getOwner());
        setType(backingProfile.getType());
        this.backingProfile = backingProfile;
    }

    @Override
    public String getSource() { return backingProfile.getSource(); }

    @Override
    public synchronized void freeze() {
        super.freeze();
        backingProfile.freeze();
    }

    @Override
    protected Object localLookup(String localName, DimensionBinding dimensionBinding) {
        Object valueInThis = super.localLookup(localName, dimensionBinding);
        if (valueInThis != null) return valueInThis;
        return backingProfile.localLookup(localName, dimensionBinding.createFor(backingProfile.getDimensions()));
    }

    protected Boolean isLocalInstanceOverridable(String localName) {
        Boolean valueInThis = super.isLocalInstanceOverridable(localName);
        if (valueInThis != null) return valueInThis;
        return backingProfile.isLocalInstanceOverridable(localName);
    }

    @Override
    protected QueryProfile createSubProfile(String name, DimensionBinding dimensionBinding) {
        Object backing = backingProfile.lookup(new CompoundName(name),
                                               true,
                                               dimensionBinding.createFor(backingProfile.getDimensions()));
        if (backing instanceof QueryProfile)
            return new BackedOverridableQueryProfile((QueryProfile)backing);
        else
            return new OverridableQueryProfile(getOwner()); // Nothing is set in this branch, so nothing to override, but need override checking
    }

    /** Returns a clone of this which can be independently overridden, but which refers to the same backing profile */
    @Override
    public BackedOverridableQueryProfile clone() {
        return (BackedOverridableQueryProfile)super.clone();
    }

    /** Returns the query profile backing this */
    public QueryProfile getBacking() { return backingProfile; }

    @Override
    public void addInherited(QueryProfile inherited) {
        backingProfile.addInherited(inherited);
    }

    void addInheritedHere(QueryProfile inherited) {
        super.addInherited(inherited);
    }

    @Override
    protected void visitVariants(boolean allowContent, QueryProfileVisitor visitor, DimensionBinding dimensionBinding) {
        super.visitVariants(allowContent, visitor, dimensionBinding);
        if (visitor.isDone()) return;
        backingProfile.visitVariants(allowContent, visitor, dimensionBinding.createFor(backingProfile.getDimensions()));
    }

    @Override
    protected void visitInherited(boolean allowContent, QueryProfileVisitor visitor, DimensionBinding dimensionBinding, QueryProfile owner) {
        super.visitInherited(allowContent, visitor, dimensionBinding, owner);
        if (visitor.isDone()) return;
        backingProfile.visitInherited(allowContent, visitor, dimensionBinding.createFor(backingProfile.getDimensions()), owner);
    }

    /** Returns a value from the content of this: The value in this, or the value from the backing if not set in this */
    protected Object getContent(String localKey) {
        Object value = super.getContent(localKey);
        if (value != null) return value;
        return backingProfile.getContent(localKey);
    }

    /**
     * Returns all the content from this:
     * All the values in this, and all values in the backing where an overriding value is not set in this
     */
    @Override
    protected Map<String, Object> getContent() {
        Map<String,Object> thisContent = super.getContent();
        Map<String,Object> backingContent = backingProfile.getContent();
        if (thisContent.isEmpty()) return backingContent; // Shortcut
        if (backingContent.isEmpty()) return thisContent; // Shortcut
        Map<String, Object> content = new HashMap<>(backingContent);
        content.putAll(thisContent);
        return content;
    }

    @Override
    public String toString() {
        return "overridable wrapper of " + backingProfile;
    }

    @Override
    public boolean isExplicit() {
        return backingProfile.isExplicit();
    }

    @Override
    public List<String> getDimensions() {
        List<String> dimensions = super.getDimensions();
        if (dimensions != null) return dimensions;
        return backingProfile.getDimensions();
    }

}
