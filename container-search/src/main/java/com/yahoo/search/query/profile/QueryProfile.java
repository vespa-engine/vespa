// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.FreezableSimpleComponent;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.processing.request.Properties;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfile;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.types.FieldDescription;
import com.yahoo.search.query.profile.types.QueryProfileFieldType;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A query profile is a data container with an id and a class (type). More precisely, it contains
 * <ul>
 *   <li>An id, on the form name:version, where the version is optional, and follows the same rules as for other search container components.
 *   <li>A class id referring to the class defining this profile (see Query Profile Classes below)
 *   <li>A (possibly empty) list of ids of inherited query profiles
 *   <li>A (possibly empty) list of declarative predicates over search request parameters which defines when this query profile is applicable (see Query Profile Selection below)
 *   <li>The data content, which consists of
 *       <ul>
 *         <li>named values
 *         <li>named references to other profiles
 *       </ul>
 * </ul>
 *
 * This serves the purpose of an intermediate format between configuration and runtime structures - the runtime
 * structure used is QueryProfileProperties.
 *
 * @author bratseth
 */
public class QueryProfile extends FreezableSimpleComponent implements Cloneable {

    /** Defines the permissible content of this, or null if any content is permissible */
    private QueryProfileType type = null;

    /** The value at this query profile - allows non-fields to have values, e.g a=value1, a.b=value2 */
    private Object value = null;

    /** The variants of this, or null if none */
    private QueryProfileVariants variants = null;

    /** The resolved variant dimensions of this, or null if none or not resolved yet (is resolved at freeze) */
    private List<String> resolvedDimensions = null;

    /** The query profiles inherited by this, or null if none */
    private List<QueryProfile> inherited = null;

    /** The content of this profile. The values may be primitives, substitutable strings or other query profiles */
    private CopyOnWriteContent content = new CopyOnWriteContent();

    /**
     * Field override settings: fieldName→OverrideValue. These overrides the override
     * setting in the type (if any) of this field). If there are no query profile level settings, this is null.
     */
    private Map<String,Boolean> overridable = null;

    /**
     * Creates a new query profile from an id.
     * The query profile can be modified freely (but not accessed) until it is {@link #freeze frozen}.
     * At that point it becomes readable but unmodifiable, which it stays until it goes out of reference.
     */
    public QueryProfile(ComponentId id) {
        super(id);
        if ( ! id.isAnonymous())
            validateName(id.getName());
    }

    /** Convenience shorthand for new QueryProfile(new ComponentId(idString)) */
    public QueryProfile(String idString) {
        this(new ComponentId(idString));
    }

    // ----------------- Public API -------------------------------------------------------------------------------

    // ----------------- Setters and getters

    /** Returns the type of this or null if it has no type */
    public QueryProfileType getType() { return type; }

    /** Sets the type of this, or set to null to not use any type checking in this profile */
    public void setType(QueryProfileType type) { this.type=type; }

    /** Returns the virtual variants of this, or null if none */
    public QueryProfileVariants getVariants() { return variants; }

    /**
     * Returns the list of profiles inherited by this.
     * Note that order matters for inherited profiles - variables are resolved depth first in the order found in
     * the inherited list. This always returns an unmodifiable list - use addInherited to add.
     */
    public List<QueryProfile> inherited() {
        if (isFrozen()) return inherited; // Frozen profiles always have an unmodifiable, non-null list
        if (inherited==null) return Collections.emptyList();
        return Collections.unmodifiableList(inherited);
    }

    /** Adds a profile to the end of the inherited list of this. Throws an exception if this is frozen. */
    public void addInherited(QueryProfile profile) {
        addInherited(profile, (DimensionValues)null);
    }

    public final void addInherited(QueryProfile profile,String[] dimensionValues) {
        addInherited(profile, DimensionValues.createFrom(dimensionValues));
    }

    /** Adds a profile to the end of the inherited list of this for the given variant. Throws an exception if this is frozen. */
    public void addInherited(QueryProfile profile, DimensionValues dimensionValues) {
        ensureNotFrozen();

        DimensionBinding dimensionBinding=DimensionBinding.createFrom(getDimensions(), dimensionValues);
        if (dimensionBinding.isNull()) {
            if (inherited == null)
                inherited = new ArrayList<>();
            inherited.add(profile);
        }
        else {
            if (variants == null)
                variants = new QueryProfileVariants(dimensionBinding.getDimensions(), this);
            variants.inherit(profile,dimensionBinding.getValues());
        }
    }

    /**
     * Returns the content fields declared in this (i.e not including those inherited) as a read-only map.
     * @throws IllegalStateException if this is frozen
     */
    public Map<String,Object> declaredContent() {
        ensureNotFrozen();
        return content.unmodifiableMap();
    }

    /**
     * Returns if the given field is declared explicitly as overridable or not in this or any <i>nested</i> profiles
     * (i.e not including overridable settings <i>inherited</i> and from <i>types</i>).
     *
     * @param  name the (possibly dotted) field name to return
     * @param  context the context in which the name is resolved, or null if none
     * @return true/false if this is declared overridable/not overridable in this instance, null if it is not
     *         given any value is <i>this</i> profile instance
     * @throws IllegalStateException if this is frozen
     */
    public Boolean isDeclaredOverridable(String name, Map<String,String> context) {
        return isDeclaredOverridable(new CompoundName(name), DimensionBinding.createFrom(getDimensions(), context));
    }

    /** Sets the dimensions over which this may vary. Note: This will erase any currently defined variants */
    public void setDimensions(String[] dimensions) {
        ensureNotFrozen();
        variants = new QueryProfileVariants(dimensions, this);
    }

    /** Returns the value set at this node, to allow non-leafs to have values. Returns null if none. */
    public Object getValue() { return value; }

    public void setValue(Object value) {
        ensureNotFrozen();
        this.value = value;
    }

    /** Returns the variant dimensions to be used in this - an unmodifiable list of dimension names */
    public List<String> getDimensions() {
        if (isFrozen()) return resolvedDimensions;
        if (variants != null) return variants.getDimensions();
        if (inherited == null) return null;
        for (QueryProfile inheritedProfile : inherited) {
            List<String> inheritedDimensions = inheritedProfile.getDimensions();
            if (inheritedDimensions != null) return inheritedDimensions;
        }
        return null;
    }

    // ----------------- Query profile facade API

    /**
     * Sets the overridability of a field in this profile,
     * this overrides the corresponding setting in the type (if any)
     */
    public final void setOverridable(String fieldName, boolean overridable, Map<String, String> context) {
        setOverridable(new CompoundName(fieldName), overridable, DimensionBinding.createFrom(getDimensions(), context));
    }

    /**
     * Return all objects that start with the given prefix path using no context. Use "" to list all.
     * <p>
     * For example, if {a.d =&gt; "a.d-value" ,a.e =&gt; "a.e-value", b.d =&gt; "b.d-value", then calling listValues("a")
     * will return {"d" =&gt; "a.d-value","e" =&gt; "a.e-value"}
     */
    public final Map<String, Object> listValues(String prefix) { return listValues(new CompoundName(prefix)); }

    /**
     * Return all objects that start with the given prefix path using no context. Use "" to list all.
     * <p>
     * For example, if {a.d =&gt; "a.d-value" ,a.e =&gt; "a.e-value", b.d =&gt; "b.d-value", then calling listValues("a")
     * will return {"d" =&gt; "a.d-value","e" =&gt; "a.e-value"}
     */
    public final Map<String, Object> listValues(CompoundName prefix) { return listValues(prefix, null); }

    /**
     * Return all objects that start with the given prefix path. Use "" to list all.
     * <p>
     * For example, if {a.d =&gt; "a.d-value" ,a.e =&gt; "a.e-value", b.d =&gt; "b.d-value", then calling listValues("a")
     * will return {"d" =&gt; "a.d-value","e" =&gt; "a.e-value"}
     */
    public final Map<String, Object> listValues(String prefix, Map<String,String> context) {
        return listValues(new CompoundName(prefix), context);
    }

    /**
     * Return all objects that start with the given prefix path. Use "" to list all.
     * <p>
     * For example, if {a.d =&gt; "a.d-value" ,a.e =&gt; "a.e-value", b.d =&gt; "b.d-value", then calling listValues("a")
     * will return {"d" =&gt; "a.d-value","e" =&gt; "a.e-value"}
     */
    public final Map<String, Object> listValues(CompoundName prefix, Map<String,String> context) {
        return listValues(prefix, context, null);
    }

    /**
     * Adds all objects that start with the given path prefix to the given value map. Use "" to list all.
     * <p>
     * For example, if {a.d =&gt; "a.d-value" ,a.e =&gt; "a.e-value", b.d =&gt; "b.d-value", then calling listValues("a")
     * will return {"d" =&gt; "a.d-value","e" =&gt; "a.e-value"}
     */
    public Map<String, Object> listValues(CompoundName prefix, Map<String, String> context, Properties substitution) {
        DimensionBinding dimensionBinding=DimensionBinding.createFrom(getDimensions(),context);

        AllValuesQueryProfileVisitor visitor=new AllValuesQueryProfileVisitor(prefix);
        accept(visitor,dimensionBinding, null);
        Map<String,Object>  values=visitor.getResult();

        if (substitution==null) return values;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue().getClass() == String.class) continue; // Shortcut
            if (entry.getValue() instanceof SubstituteString)
                entry.setValue(((SubstituteString)entry.getValue()).substitute(context,substitution));
        }
        return values;
    }

    /**
     * Lists types reachable from this, indexed by the prefix having that type.
     * If this is itself typed, this' type will be included with an empty prefix
     */
    public Map<CompoundName, QueryProfileType> listTypes(CompoundName prefix, Map<String, String> context) {
        DimensionBinding dimensionBinding = DimensionBinding.createFrom(getDimensions(), context);
        AllTypesQueryProfileVisitor visitor = new AllTypesQueryProfileVisitor(prefix);
        accept(visitor, dimensionBinding, null);
        return visitor.getResult();
    }

    /**
     * Lists references reachable from this.
     */
    Set<CompoundName> listReferences(CompoundName prefix, Map<String, String> context) {
        DimensionBinding dimensionBinding = DimensionBinding.createFrom(getDimensions(),context);
        AllReferencesQueryProfileVisitor visitor = new AllReferencesQueryProfileVisitor(prefix);
        accept(visitor,dimensionBinding,null);
        return visitor.getResult();
    }

    /**
     * Lists every entry (value or reference) reachable from this which is not overridable
     */
    Set<CompoundName> listUnoverridable(CompoundName prefix, Map<String, String> context) {
        DimensionBinding dimensionBinding = DimensionBinding.createFrom(getDimensions(),context);
        AllUnoverridableQueryProfileVisitor visitor = new AllUnoverridableQueryProfileVisitor(prefix);
        accept(visitor, dimensionBinding, null);
        return visitor.getResult();
    }

    /**
     * Returns a value from this query profile by resolving the given name:
     * <ul>
     *   <li>The name up to the first dot is the value looked up in the value of this profile
     *   <li>The rest of the name (if any) is used as the name to look up in the referenced query profile
     * </ul>
     *
     * If this name does not resolve <i>completely</i> into a value in this or any inherited profile, null is returned.
     */
    public final Object get(String name) { return get(name,(Map<String,String>)null); }

    /** Returns a value from this using the given property context for resolution and using this for substitution */
    public final Object get(String name, Map<String, String> context) {
        return get(name, context, null);
    }

    /** Returns a value from this using the given dimensions for resolution */
    public final Object get(String name, String[] dimensionValues) {
        return get(name, dimensionValues,null);
    }

    public final Object get(String name, String[] dimensionValues, Properties substitution) {
        return get(name, DimensionValues.createFrom(dimensionValues), substitution);
    }

    /** Returns a value from this using the given dimensions for resolution */
    public final Object get(String name, DimensionValues dimensionValues, Properties substitution) {
        return get(name, DimensionBinding.createFrom(getDimensions(), dimensionValues), substitution);
    }

    public final Object get(String name, Map<String,String> context, Properties substitution) {
        return get(name, DimensionBinding.createFrom(getDimensions(), context), substitution);
    }

    public final Object get(CompoundName name, Map<String,String> context, Properties substitution) {
        return get(name, DimensionBinding.createFrom(getDimensions(), context), substitution);
    }

    final Object get(String name, DimensionBinding binding,Properties substitution) {
        return get(new CompoundName(name), binding, substitution);
    }

    final Object get(CompoundName name, DimensionBinding binding, Properties substitution) {
        Object node = get(name, binding);
        if (node != null && node.getClass() == String.class) return node; // Shortcut
        if (node instanceof SubstituteString) return ((SubstituteString)node).substitute(binding.getContext(), substitution);
        return node;
    }

    final Object get(CompoundName name,DimensionBinding dimensionBinding) {
        return lookup(name,false,dimensionBinding);
    }

    /**
     * Returns the node at the position prescribed by the given name (without doing substitutions) -
     * a primitive value, a substitutable string, a query profile, or null if not found.
     */
    public final Object lookup(String name, Map<String,String> context) {
        return lookup(new CompoundName(name),true,DimensionBinding.createFrom(getDimensions(),context));
    }

    /** Sets a value in this or any nested profile using null as context */
    public final void set(String name, Object value, QueryProfileRegistry registry) {
        set(name,value,(Map<String,String>)null, registry);
    }

    /**
     * Sets a value in this or any nested profile. Any missing structure needed to set this will be created.
     * If this value is already set, this will overwrite the previous value.
     *
     * @param name the name of the field, possibly a dotted name which will cause setting of a variable in a subprofile
     * @param value the value to assign to the name, a primitive wrapper, string or a query profile
     * @param context the context used to resolve where this value should be set, or null if none
     * @throws IllegalArgumentException if the given name is illegal given the types of this or any nested query profile
     * @throws IllegalStateException if this query profile is frozen
     */
    public final void set(CompoundName name,Object value,Map<String,String> context, QueryProfileRegistry registry) {
        set(name, value, DimensionBinding.createFrom(getDimensions(), context), registry);
    }

    public final void set(String name,Object value,Map<String,String> context, QueryProfileRegistry registry) {
        set(new CompoundName(name), value, DimensionBinding.createFrom(getDimensions(), context), registry);
    }

    public final void set(String name,Object value,String[] dimensionValues, QueryProfileRegistry registry) {
        set(name,value,DimensionValues.createFrom(dimensionValues), registry);
    }

    /**
     * Sets a value in this or any nested profile. Any missing structure needed to set this will be created.
     * If this value is already set, this will overwrite the previous value.
     *
     * @param name the name of the field, possibly a dotted name which will cause setting of a variable in a subprofile
     * @param value the value to assign to the name, a primitive wrapper, string or a query profile
     * @param dimensionValues the dimension values - will be matched by order to the dimensions set in this - if this is
     *        shorter or longer than the number of dimensions it will be adjusted as needed
     * @param registry the registry used to resolve query profile references. If null is passed query profile references
     *        will cause an exception
     * @throws IllegalArgumentException if the given name is illegal given the types of this or any nested query profile
     * @throws IllegalStateException if this query profile is frozen
     */
    public final void set(String name,Object value,DimensionValues dimensionValues, QueryProfileRegistry registry) {
        set(new CompoundName(name), value, DimensionBinding.createFrom(getDimensions(), dimensionValues), registry);
    }

    // ----------------- Misc

    public boolean isExplicit() {
        return !getId().isAnonymous();
    }

    /**
     * Switches this from write-only to read-only mode.
     * This profile can never be modified again after this method returns.
     * Calling this on an already frozen profile has no effect.
     * <p>
     * Calling this will also freeze any profiles inherited and referenced by this.
     */
    // TODO: Remove/simplify as query profiles are not used at query time
    public synchronized void freeze() {
        if (isFrozen()) return;

        resolvedDimensions=getDimensions();

        if (variants !=null)
            variants.freeze();

        if (inherited!=null) {
            for (QueryProfile inheritedProfile : inherited)
                inheritedProfile.freeze();
        }

        content.freeze();

        inherited= inherited==null ? ImmutableList.of() : ImmutableList.copyOf(inherited);

        super.freeze();
    }

    @Override
    public String toString() {
        return "query profile '" + getId()  + "'" + (type!=null ? " of type '" + type.getId() + "'" : "");
    }

    /**
     * Returns a clone of this. The clone will not be frozen and will contain copied inherited and content collections
     * pointing to the same values as this.
     */
    @Override
    public QueryProfile clone() {
        if (isFrozen()) return this;
        QueryProfile clone = (QueryProfile)super.clone();
        if (variants != null)
            clone.variants = variants.clone();
        if (inherited != null)
            clone.inherited = new ArrayList<>(inherited);

        if (this.content != null)
            clone.content = content.clone();

        return clone;
    }

    /**
     * Clones a value of a type which may appear in a query profile if cloning is necessary (i.e if it is
     * not immutable). Returns the input type otherwise.
     */
    static Object cloneIfNecessary(Object object) {
        if (object instanceof QueryProfile) return ((QueryProfile)object).clone();
        return object; // Other types are immutable
    }

    /** Throws IllegalArgumentException if the given string is not a valid query profile name */
    public static void validateName(String name) {
        Matcher nameMatcher = namePattern.matcher(name);
        if ( ! nameMatcher.matches())
            throw new IllegalArgumentException("Illegal name '" + name + "'");
    }

    // ----------------- For subclass use --------------------------------------------------------------------

    /** Override this to intercept all writes to this profile (or any nested profiles) */
    protected void set(CompoundName name, Object value, DimensionBinding binding, QueryProfileRegistry registry) {
        try {
            setNode(name, value, null, binding, registry);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Could not set '" + name + "' to '" + value + "'", e);
        }
    }

    /** Returns this value, or its corresponding substitution string if it contains substitutions */
    protected Object convertToSubstitutionString(Object value) {
        if (value==null) return value;
        if (value.getClass()!=String.class) return value;
        SubstituteString substituteString=SubstituteString.create((String)value);
        if (substituteString==null) return value;
        return substituteString;
    }

    /** Returns the field description of this field, or null if it is not typed */
    protected FieldDescription getFieldDescription(CompoundName name, DimensionBinding binding) {
        FieldDescriptionQueryProfileVisitor visitor=new FieldDescriptionQueryProfileVisitor(name.asList());
        accept(visitor, binding,null);
        return visitor.result();
    }

    /**
     * Returns true if this value is definitely overridable in this (set and not unoverridable),
     * false if it is declared unoverridable (in instance or type), and null if this profile has no
     * opinion on the matter because the value is not set in this.
     */
    Boolean isLocalOverridable(String localName,DimensionBinding binding) {
        if (localLookup(localName, binding)==null) return null; // Not set
        Boolean isLocalInstanceOverridable=isLocalInstanceOverridable(localName);
        if (isLocalInstanceOverridable!=null)
            return isLocalInstanceOverridable.booleanValue();
        if (type!=null) return type.isOverridable(localName);
        return true;
    }

    protected Boolean isLocalInstanceOverridable(String localName) {
        if (overridable==null) return null;
        return overridable.get(localName);
    }

    protected Object lookup(CompoundName name,boolean allowQueryProfileResult, DimensionBinding dimensionBinding) {
        SingleValueQueryProfileVisitor visitor=new SingleValueQueryProfileVisitor(name.asList(),allowQueryProfileResult);
        accept(visitor,dimensionBinding,null);
        return visitor.getResult();
    }

    protected final void accept(QueryProfileVisitor visitor,DimensionBinding dimensionBinding, QueryProfile owner) {
        acceptAndEnter("", visitor, dimensionBinding, owner);
    }

    void acceptAndEnter(String key, QueryProfileVisitor visitor,DimensionBinding dimensionBinding, QueryProfile owner) {
        boolean allowContent=visitor.enter(key);
        accept(allowContent, visitor, dimensionBinding, owner);
        if (allowContent)
            visitor.leave(key);
    }

    /**
     * Visit the profiles and values referenced from this in order of decreasing precedence
     *
     * @param allowContent whether content in this should be visited
     * @param visitor the visitor
     * @param dimensionBinding the dimension binding to use
     */
    final void accept(boolean allowContent,QueryProfileVisitor visitor, DimensionBinding dimensionBinding, QueryProfile owner) {
        visitor.onQueryProfile(this, dimensionBinding, owner);
        if (visitor.isDone()) return;

        visitVariants(allowContent,visitor,dimensionBinding);
        if (visitor.isDone()) return;

        if (allowContent) {
            visitContent(visitor,dimensionBinding);
            if (visitor.isDone()) return;
        }

        if (visitor.visitInherited())
            visitInherited(allowContent, visitor, dimensionBinding, owner);
    }

    protected void visitVariants(boolean allowContent,QueryProfileVisitor visitor,DimensionBinding dimensionBinding) {
        if (getVariants()!=null)
            getVariants().accept(allowContent, getType(), visitor, dimensionBinding);
    }

    protected void visitInherited(boolean allowContent,QueryProfileVisitor visitor,DimensionBinding dimensionBinding, QueryProfile owner) {
        if (inherited==null) return;
        for (QueryProfile inheritedProfile : inherited) {
            inheritedProfile.accept(allowContent,visitor,dimensionBinding.createFor(inheritedProfile.getDimensions()), owner);
            if (visitor.isDone()) return;
        }
    }

    private void visitContent(QueryProfileVisitor visitor,DimensionBinding dimensionBinding) {
        String contentKey=visitor.getLocalKey();

        // Visit this' content
        if (contentKey!=null) { // Get only the content of the current key
            if (type!=null)
                contentKey=type.unalias(contentKey);
            visitor.acceptValue(contentKey, getContent(contentKey), dimensionBinding, this);
        }
        else { // get all content in this
            for (Map.Entry<String,Object> entry : getContent().entrySet()) {
                visitor.acceptValue(entry.getKey(), entry.getValue(), dimensionBinding, this);
                if (visitor.isDone()) return;
            }
        }
    }

    /** Returns a value from the content of this, or null if not present */
    protected Object getContent(String key) {
        return content.get(key);
    }

    /** Returns all the content from this as an unmodifiable map */
    protected Map<String,Object> getContent() {
        return content.unmodifiableMap();
    }

    /** Sets the value of a node in <i>this</i> profile - the local name given must not be nested (contain dots) */
    protected QueryProfile setLocalNode(String localName, Object value,QueryProfileType parentType,
                                        DimensionBinding dimensionBinding, QueryProfileRegistry registry) {
        if (parentType!=null && type==null && !isFrozen())
            type=parentType;

        value=checkAndConvertAssignment(localName, value, registry);
        localPut(localName,value,dimensionBinding);
        return this;
    }

    /**
     * Combines an existing and a new value for a query property key.
     * Return the new object to add to the state of the owning profile (/variant), or null if no new value needs to
     * be added (usually because the new value was added to the existing).
     */
    static Object combineValues(Object newValue, Object existingValue) {
        if (newValue instanceof QueryProfile) {
            QueryProfile newProfile=(QueryProfile)newValue;
            if ( existingValue==null || ! (existingValue instanceof QueryProfile)) {
                if (!isModifiable(newProfile))
                    newProfile=new BackedOverridableQueryProfile(newProfile); // Make the query profile reference overridable
                newProfile.value=existingValue;
                return newProfile;
            }

            // if both are profiles:
            return combineProfiles(newProfile,(QueryProfile)existingValue);
        }
        else {
            if (existingValue instanceof QueryProfile) { // we need to set a non-leaf value on a query profile
                QueryProfile existingProfile=(QueryProfile)existingValue;
                if (isModifiable(existingProfile)) {
                    existingProfile.setValue(newValue);
                    return null;
                }
                else {
                    QueryProfile existingOverridable = new BackedOverridableQueryProfile((QueryProfile)existingValue);
                    existingOverridable.setValue(newValue);
                    return existingOverridable;
                }
            }
            else {
                return newValue;
            }
        }
    }

    private static QueryProfile combineProfiles(QueryProfile newProfile,QueryProfile existingProfile) {
        QueryProfile returnValue=null;
        QueryProfile existingModifiable;

        // Ensure the existing profile is modifiable
        if (existingProfile.getClass()==QueryProfile.class) {
            existingModifiable = new BackedOverridableQueryProfile(existingProfile);
            returnValue=existingModifiable;
        }
        else { // is an overridable wrapper
            existingModifiable=existingProfile; // May be used as-is
        }

        // Make the existing profile inherit the new one
        if (existingModifiable instanceof BackedOverridableQueryProfile)
            ((BackedOverridableQueryProfile)existingModifiable).addInheritedHere(newProfile);
        else
            existingModifiable.addInherited(newProfile);

        // Remove content from the existing which the new one does not allow overrides of
        if (existingModifiable.content!=null) {
            for (String key : existingModifiable.content.unmodifiableMap().keySet()) {
                if ( ! newProfile.isLocalOverridable(key, null)) {
                    existingModifiable.content.remove(key);
                }
            }
        }

        return returnValue;
    }

    /** Returns whether the given profile may be modified from this profile */
    private static boolean isModifiable(QueryProfile profile) {
        if (profile.isFrozen()) return false;
        if ( ! profile.isExplicit()) return true; // Implicitly defined from this - ok to modify then
        if (! (profile instanceof BackedOverridableQueryProfile)) return false;
        return true;
    }

    /**
     * Converts to the type of the receiving field, if possible and necessary.
     *
     * @return the value to be assigned: the original or a converted value
     * @throws IllegalArgumentException if the assignment is illegal
     */
    protected Object checkAndConvertAssignment(String localName, Object value, QueryProfileRegistry registry) {
        if (type==null) return value; // no type checking

        FieldDescription fieldDescription=type.getField(localName);
        if (fieldDescription==null) {
            if (type.isStrict())
                throw new IllegalArgumentException("'" + localName + "' is not declared in " + type + ", and the type is strict");
            return value;
        }

        if (registry == null && (fieldDescription.getType() instanceof QueryProfileFieldType))
            throw new IllegalArgumentException("A registry was not passed: Query profile references is not supported");
        Object convertedValue = fieldDescription.getType().convertFrom(value, registry);
        if (convertedValue == null)
            throw new IllegalArgumentException("'" + value + "' is not a " + fieldDescription.getType().toInstanceDescription());
        return convertedValue;
    }

    /**
     * Looks up all inherited profiles and adds any that matches this name.
     * This default implementation returns an empty profile.
     */
    protected QueryProfile createSubProfile(String name,DimensionBinding dimensionBinding) {
        QueryProfile queryProfile = new QueryProfile(ComponentId.createAnonymousComponentId(name));
        return queryProfile;
    }

    /** Do a variant-aware content lookup in this */
    protected Object localLookup(String name, DimensionBinding dimensionBinding) {
        Object node = null;
        if ( variants != null && !dimensionBinding.isNull())
            node = variants.get(name,type,true,dimensionBinding);
        if (node == null)
            node = content == null ? null : content.get(name);
        return node;
    }

    // ----------------- Private  ----------------------------------------------------------------------------------

    private Boolean isDeclaredOverridable(CompoundName name,DimensionBinding dimensionBinding) {
        QueryProfile parent = lookupParentExact(name, true, dimensionBinding);
        if (parent.overridable == null) return null;
        return parent.overridable.get(name.last());
    }

    /**
     * Sets the overridability of a field in this profile,
     * this overrides the corresponding setting in the type (if any)
     */
    private void setOverridable(CompoundName fieldName,boolean overridable,DimensionBinding dimensionBinding) {
        QueryProfile parent = lookupParentExact(fieldName, true, dimensionBinding);
        if (parent.overridable == null)
            parent.overridable = new HashMap<>();
        parent.overridable.put(fieldName.last(), overridable);
    }

    /** Sets a value to a (possibly non-local) node. The parent query profile holding the value is returned */
    private void setNode(CompoundName name, Object value, QueryProfileType parentType,
                         DimensionBinding dimensionBinding, QueryProfileRegistry registry) {
        ensureNotFrozen();
        if (name.isCompound()) {
            QueryProfile parent = getQueryProfileExact(name.first(), true, dimensionBinding);
            parent.setNode(name.rest(), value,parentType, dimensionBinding.createFor(parent.getDimensions()), registry);
        }
        else {
            setLocalNode(name.toString(), value,parentType, dimensionBinding, registry);
        }
    }

    /**
     * Looks up and, if necessary, creates, the query profile which should hold the given local name portion of the
     * given name. If the name contains no dots, this is returned.
     *
     * @param name the name of the variable to lookup the parent of
     * @param create whether or not to create the parent if it is not present
     * @return the parent, or null if not present and created is false
     */
    private QueryProfile lookupParentExact(CompoundName name, boolean create, DimensionBinding dimensionBinding) {
        CompoundName rest = name.rest();
        if (rest.isEmpty()) return this;

        QueryProfile topmostParent = getQueryProfileExact(name.first(), create, dimensionBinding);
        if (topmostParent == null) return null;
        return topmostParent.lookupParentExact(rest, create, dimensionBinding.createFor(topmostParent.getDimensions()));
    }

    /**
     * Returns a query profile from this by name
     *
     * @param localName the local name of the profile in this, this is never a compound
     * @param create whether the profile should be created if missing
     * @return the created profile, or null if not present, and create is false
     */
    private QueryProfile getQueryProfileExact(String localName, boolean create, DimensionBinding dimensionBinding) {
        Object node = localExactLookup(localName, dimensionBinding);
        if (node != null && node instanceof QueryProfile) {
            return (QueryProfile)node;
        }
        if (!create) return null;

        QueryProfile queryProfile=createSubProfile(localName,dimensionBinding);
        if (type != null) {
            Class<?> legalClass=type.getValueClass(localName);
            if (legalClass == null || ! legalClass.isInstance(queryProfile))
                throw new RuntimeException("'" + localName + "' is not a legal query profile reference name in " + this);
            queryProfile.setType(type.getType(localName));
        }
        localPut(localName,queryProfile,dimensionBinding);
        return queryProfile;
    }

    /** Do a variant-aware content lookup in this - without looking in any wrapped content. But by matching variant bindings exactly only */
    private Object localExactLookup(String name, DimensionBinding dimensionBinding) {
        if (dimensionBinding.isNull()) return content == null ? null : content.get(name);
        if (variants == null) return null;
        QueryProfileVariant variant = variants.getVariant(dimensionBinding.getValues(),false);
        if (variant == null) return null;
        return variant.values().get(name);
    }

    /** Sets a value directly in this query profile (unless frozen) */
    private void localPut(String localName,Object value, DimensionBinding dimensionBinding) {
        ensureNotFrozen();

        if (type != null)
            localName = type.unalias(localName);

        validateName(localName);
        value = convertToSubstitutionString(value);

        if (dimensionBinding.isNull()) {
            Object combinedValue;
            if (value instanceof QueryProfile)
                combinedValue = combineValues(value,content==null ? null : content.get(localName));
            else
                combinedValue = combineValues(value, localLookup(localName, dimensionBinding));

            if (combinedValue!=null)
                content.put(localName,combinedValue);
        }
        else {
            if (variants == null)
                variants = new QueryProfileVariants(dimensionBinding.getDimensions(), this);
            variants.set(localName,dimensionBinding.getValues(),value);
        }
    }

    private static final Pattern namePattern = Pattern.compile("[$a-zA-Z_/][-$a-zA-Z0-9_/()]*");

    /**
     * Returns a compiled version of this which produces faster lookup times
     *
     * @param registry the registry this will be added to by the caller, or null if none
     */
    public CompiledQueryProfile compile(CompiledQueryProfileRegistry registry) {
        return QueryProfileCompiler.compile(this, registry);
    }

}
