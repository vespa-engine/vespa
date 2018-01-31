// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.FreezableSimpleComponent;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.query.profile.QueryProfile;

import java.util.*;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * Defines a kind of query profiles
 *
 * @author bratseth
 */
public class QueryProfileType extends FreezableSimpleComponent {

    /** The fields of this query profile type */
    private Map<String, FieldDescription> fields = new HashMap<>();

    /** The query profile types this inherits */
    private List<QueryProfileType> inherited = new ArrayList<>();

    /** If this is true, keys which are not declared in this type cannot be set in instances */
    private boolean strict = false;

    /** True if the name of instances of this profile should be matched as path names, see QueryProfileRegistry */
    private boolean matchAsPath = false;

    private boolean builtin = false;

    /** Aliases *from* any strings *to* field names. Aliases are case insensitive */
    private Map<String, String> aliases = null;

    public QueryProfileType(String idString) {
        this(new ComponentId(idString));
    }

    public QueryProfileType(ComponentId id) {
        super(id);
        QueryProfile.validateName(id.getName());
    }

    private QueryProfileType(ComponentId id, Map<String, FieldDescription> fields, List<QueryProfileType> inherited,
                            boolean strict, boolean matchAsPath, boolean builtin, Map<String,String> aliases) {
        super(id);
        this.fields = new HashMap<>(fields);
        this.inherited = new ArrayList<>(inherited);
        this.strict = strict;
        this.matchAsPath = matchAsPath;
        this.builtin = builtin;
        this.aliases = aliases == null ? null : new HashMap<>(aliases);
    }

    /** Return this is it is not frozen, returns a modifiable deeply unfrozen copy otherwise */
    public QueryProfileType unfrozen() {
        if ( ! isFrozen()) return this;

        // Unfreeze inherited query profile references
        List<QueryProfileType> unfrozenInherited = new ArrayList<>();
        for (QueryProfileType inheritedType : inherited) {
            unfrozenInherited.add(inheritedType.unfrozen());
        }

        // Unfreeze nested query profile references
        Map<String, FieldDescription> unfrozenFields = new HashMap<>();
        for (Map.Entry<String, FieldDescription> field : fields.entrySet()) {
            FieldDescription unfrozenFieldValue = field.getValue();
            if (field.getValue().getType() instanceof QueryProfileFieldType) {
                QueryProfileFieldType queryProfileFieldType = (QueryProfileFieldType)field.getValue().getType();
                if (queryProfileFieldType.getQueryProfileType() != null) {
                    QueryProfileFieldType unfrozenType =
                            new QueryProfileFieldType(queryProfileFieldType.getQueryProfileType().unfrozen());
                    unfrozenFieldValue = field.getValue().withType(unfrozenType);
                }
            }
            unfrozenFields.put(field.getKey(), unfrozenFieldValue);
        }

        return new QueryProfileType(getId(), unfrozenFields, unfrozenInherited, strict, matchAsPath, builtin, aliases);
    }

    /** Mark this type as built into the system. Do not use */
    public void setBuiltin(boolean builtin) { this.builtin=builtin; }

    /** Returns whether this type is built into the system */
    public boolean isBuiltin() { return builtin; }

    /**
     * Returns the query profile types inherited from this (never null).
     * If this profile type is not frozen, this list can be modified to change the set of inherited types.
     * If it is frozen, the returned list is immutable.
     */
    public List<QueryProfileType> inherited() { return inherited; }

    /**
     * Returns the fields declared in this (i.e not including those inherited) as an immutable map.
     *
     * @throws IllegalStateException if this is frozen
     */
    public Map<String, FieldDescription> declaredFields() {
        ensureNotFrozen();
        return Collections.unmodifiableMap(fields);
    }

    /**
     * Returns true if <i>this</i> is declared strict.
     *
     * @throws IllegalStateException if this is frozen
     */
    public boolean isDeclaredStrict() {
        ensureNotFrozen();
        return strict;
    }

    /**
     * Returns true if <i>this</i> is declared as match as path.
     *
     * @throws IllegalStateException if this is frozen
     */
    public boolean getDeclaredMatchAsPath() {
        ensureNotFrozen();
        return matchAsPath;
    }

    /** Set whether nondeclared fields are permissible. Throws an exception if this is frozen. */
    public void setStrict(boolean strict) {
        ensureNotFrozen();
        this.strict=strict;
    }

    /** Returns whether field not declared in this type is permissible in instances. Default is false: Additional values are allowed */
    public boolean isStrict() {
        if (isFrozen()) return strict;

        // Check if any of this or an inherited is true
        if (strict) return true;
        for (QueryProfileType inheritedType : inherited)
            if (inheritedType.isStrict()) return true;
        return false;
    }

    /** Returns whether instances of this should be matched as path names. Throws if this is frozen. */
    public void setMatchAsPath(boolean matchAsPath) {
        ensureNotFrozen();
        this.matchAsPath=matchAsPath;
    }

    /** Returns whether instances of this should be matched as path names. Default is false: Use exact name matching. */
    public boolean getMatchAsPath() {
        if (isFrozen()) return matchAsPath;

        // Check if any of this or an inherited is true
        if (matchAsPath) return true;
        for (QueryProfileType inheritedType : inherited)
            if (inheritedType.getMatchAsPath()) return true;
        return false;
    }

    public void freeze() {
        if (isFrozen()) return;
        // Flatten the inheritance hierarchy into this to facilitate faster lookup
        for (QueryProfileType inheritedType : inherited) {
            for (FieldDescription field : inheritedType.fields().values())
                if ( ! fields.containsKey(field.getName()))
                    fields.put(field.getName(),field);
        }
        fields = ImmutableMap.copyOf(fields);
        inherited = ImmutableList.copyOf(inherited);
        strict = isStrict();
        matchAsPath = getMatchAsPath();
        super.freeze();
    }

    /**
     * Returns whether the given field name is overridable in this type.
     * Default: true (so all non-declared fields returns true)
     */
    public boolean isOverridable(String fieldName) {
        FieldDescription field=getField(fieldName);
        if (field==null) return true;
        return field.isOverridable();
    }

    /**
     * Returns the permissible class for the value of the given name in this type
     *
     * @return the permissible class for a value, <code>Object</code> if all types are legal,
     *         null if no types are legal (i.e if the name is not legal)
     */
    public Class<?> getValueClass(String name) {
        FieldDescription fieldDescription=getField(name);
        if (fieldDescription==null) {
            if (strict)
                return null; // Undefined -> Not legal
            else
                return Object.class; // Undefined -> Anything is legal
        }
        return fieldDescription.getType().getValueClass();
    }

    /** Returns the type of the given query profile type declared as a field in this */
    public QueryProfileType getType(String localName) {
        FieldDescription fieldDescription=getField(localName);
        if (fieldDescription ==null) return null;
        if ( ! (fieldDescription.getType() instanceof QueryProfileFieldType)) return null;
        return ((QueryProfileFieldType) fieldDescription.getType()).getQueryProfileType();
    }

    /**
     * Returns the description of the field with the given name in this type or an inherited type
     * (depth first left to right search). Returns null if the field is not defined in this or an inherited profile.
     */
    public FieldDescription getField(String name) {
        FieldDescription field=fields.get(name);
        if ( field!=null ) return field;

        if ( isFrozen() ) return null; // Inherited are collapsed into this

        for (QueryProfileType inheritedType : this.inherited() ) {
            field=inheritedType.getField(name);
            if (field!=null) return field;
        }

        return null;
    }

    /**
     * Removes a field from this (not from any inherited profile)
     *
     * @return the removed field or null if none
     * @throws IllegalStateException if this is frozen
     */
    public FieldDescription removeField(String fieldName) {
        ensureNotFrozen();
        return fields.remove(fieldName);
    }

    /**
     * Adds a field to this, without associating with a type registry; field descriptions with compound
     * is not be supported.
     *
     * @throws IllegalStateException if this is frozen
     */
    public void addField(FieldDescription fieldDescription) {
        // Compound names translates to new types, which must be added to a supplied registry
        if (fieldDescription.getCompoundName().isCompound())
            throw new IllegalArgumentException("Adding compound names is only legal when supplying a registry");
        addField(fieldDescription, null);
    }

    /**
     * Adds a field to this
     *
     * @throws IllegalStateException if this is frozen
     */
    public void addField(FieldDescription fieldDescription, QueryProfileTypeRegistry registry) {
        CompoundName name = fieldDescription.getCompoundName();
        if (name.isCompound()) {
            // Add (/to) a query profile type containing the rest of the name.
            // (we do not need the field description settings for intermediate query profile types
            // as the leaf entry will enforce them)
            QueryProfileType type = getOrCreateQueryProfileType(name.first(), registry);
            type.addField(fieldDescription.withName(name.rest()), registry);
        }
        else {
            ensureNotFrozen();
            fields.put(fieldDescription.getName(), fieldDescription);
        }

        for (String alias : fieldDescription.getAliases())
            addAlias(alias, fieldDescription.getName());
    }

    private QueryProfileType getOrCreateQueryProfileType(String name, QueryProfileTypeRegistry registry) {
        FieldDescription fieldDescription = getField(name);
        if (fieldDescription != null) {
            if ( ! ( fieldDescription.getType() instanceof QueryProfileFieldType))
                throw new IllegalArgumentException("Cannot use name '" + name + "' as a prefix because it is " +
                                                   "already a " + fieldDescription.getType());
            QueryProfileFieldType fieldType = (QueryProfileFieldType) fieldDescription.getType();
            QueryProfileType type = fieldType.getQueryProfileType();
            if (type == null) { // an as-yet untyped reference; add type
                type = new QueryProfileType(name);
                registry.register(type.getId(), type);
                fields.put(name, fieldDescription.withType(new QueryProfileFieldType(type)));
            }
            return type;
        }
        else {
            QueryProfileType type = new QueryProfileType(name);
            registry.register(type.getId(), type);
            fields.put(name, new FieldDescription(name, new QueryProfileFieldType(type)));
            return type;
        }
    }

    private void addAlias(String alias,String field) {
        ensureNotFrozen();
        if (aliases==null)
            aliases=new HashMap<>();
        aliases.put(toLowerCase(alias),field);
    }

    /** Returns all the fields of this profile type and all types it inherits as a read-only map */
    public Map<String,FieldDescription> fields() {
        if (isFrozen()) return fields;
        if (inherited().size()==0) return Collections.unmodifiableMap(fields);

        // Collapse inherited
        Map<String,FieldDescription> allFields=new HashMap<>(fields);
        for (QueryProfileType inheritedType : inherited)
            allFields.putAll(inheritedType.fields());
        return Collections.unmodifiableMap(allFields);
    }

    /**
     * Returns the alias to field mapping of this type as a read-only map. This is never null.
     * Note that all keys are lower-cased because aliases are case-insensitive
     */
    public Map<String,String> aliases() {
        if (isFrozen()) return aliases;
        if (aliases == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(aliases);
    }

    /** Returns the field name of an alias or field name */
    public String unalias(String aliasOrField) {
        if (aliases==null || aliases.isEmpty()) return aliasOrField;
        String field=aliases.get(toLowerCase(aliasOrField));
        if (field!=null) return field;
        return aliasOrField;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    /** Two types are equal if they have the same id */
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof QueryProfileType)) return false;
        QueryProfileType other = (QueryProfileType)o;
        return other.getId().equals(this.getId());
    }

    public String toString() {
        return "query profile type '" + getId() + "'";
    }

}
