// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yahoo.component.provider.Freezable;
import com.yahoo.search.query.profile.types.QueryProfileType;

import java.util.*;

/**
 * This class represent a set of query profiles virtually - rather
 * than storing and instantiating each profile this structure represents explicitly only
 * the values set in the various virtual profiles. The set of virtual profiles are defined by a set of
 * <i>dimensions</i>. Values may be set for any point in this multi-dimensional space, and may also be set for
 * any regular hyper-region by setting values for any point in certain of these dimensions.
 * The set of virtual profiles defined by this consists of all the combinations of dimension points for
 * which one or more values is set in this, as well as any possible less specified regions.
 * <p>
 * A set of virtual profiles are always owned by a single profile, which is also their parent
 * in the inheritance hierarchy.
 *
 * @author bratseth
 */
public class QueryProfileVariants implements Freezable, Cloneable {

    private boolean frozen = false;

    /** Properties indexed by name, to support fast lookup of single values */
    private Map<String, FieldValues> fieldValuesByName = new HashMap<>();

    /** The inherited profiles for various dimensions settings - a set of fieldvalues of List&lt;QueryProfile&gt; */
    private FieldValues inheritedProfiles = new FieldValues();

    /**
     * Field and inherited profiles sorted by specificity used for all-value visiting.
     * This is the same as how the source data looks (apart from the sorting).
     */
    private List<QueryProfileVariant> variants = new ArrayList<>();

    /**
     * The names of the dimensions (which are possible properties in the context given on lookup) of this.
     * Order matters - more specific values to the left in this list are more significant than more specific values
     * to the right
     */
    private final List<String> dimensions;

    /** The query profile this variants of */
    private final QueryProfile owner;

    /**
     * Creates a set of virtual query profiles which may return varying values over the set of dimensions given.
     * Each dimension is a name for which a key-value may be supplied in the context properties
     * on lookup time to influence the value returned.
     */
    public QueryProfileVariants(String[] dimensions, QueryProfile owner) {
        this(Arrays.asList(dimensions), owner);
    }

    /**
     * Creates a set of virtual query profiles which may return varying values over the set of dimensions given.
     * Each dimension is a name for which a key-value may be supplied in the context properties
     * on lookup time to influence the value returned.
     *
     * @param dimensions the dimension names this may vary over. The list gets owned by this, so it must not be further
     *        modified from outside). This will not modify the list.
     */
    public QueryProfileVariants(List<String> dimensions, QueryProfile owner) {
        // Note: This is not made unmodifiable (here or in freeze) because we depend on map identity comparisons of this
        // list (in dimensionBinding) for performance reasons.
        this.dimensions = dimensions;
        this.owner = owner;
    }

    /** Irreversibly prevents any further modifications to this */
    public void freeze() {
        if (frozen) return;
        for (FieldValues fieldValues : fieldValuesByName.values())
            fieldValues.freeze();
        fieldValuesByName = ImmutableMap.copyOf(fieldValuesByName);
        inheritedProfiles.freeze();

        Collections.sort(variants);
        for (QueryProfileVariant variant : variants)
            variant.freeze();
        variants = ImmutableList.copyOf(variants);

        frozen=true;
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

    /** Visits the most specific match to the dimension binding of each variable (or the one named by the visitor) */
    void accept(boolean allowContent,
                QueryProfileType type,
                QueryProfileVisitor visitor,
                DimensionBinding dimensionBinding) {
        String contentName = null;
        if (allowContent)
            contentName = visitor.getLocalKey();

        if (contentName != null) {
            if (type != null)
                contentName = type.unalias(contentName);
            acceptSingleValue(contentName, allowContent, visitor, dimensionBinding); // Special cased for performance
        }
        else {
            acceptAllValues(allowContent, visitor, type, dimensionBinding);
        }
    }

    void acceptSingleValue(String name,
                           boolean allowContent,
                           QueryProfileVisitor visitor,
                           DimensionBinding dimensionBinding) {
        FieldValues fieldValues = fieldValuesByName.get(name);
        if (fieldValues == null || ! allowContent)
            fieldValues = new FieldValues();

        fieldValues.sort();
        inheritedProfiles.sort();

        int inheritedIndex = 0;
        int fieldIndex = 0;
        // Go through both the fields and the inherited profiles at the same time and try the single must specific pick
        // from either of the lists at each step
        while(fieldIndex < fieldValues.size() || inheritedIndex < inheritedProfiles.size()) {
            // Get the next most specific from field and inherited
            FieldValue fieldValue = fieldValues.getIfExists(fieldIndex);
            FieldValue inheritedProfileValue = inheritedProfiles.getIfExists(inheritedIndex);

            // Try the most specific first, then the other
            if (inheritedProfileValue == null || (fieldValue != null && fieldValue.compareTo(inheritedProfileValue) <= 0)) { // Field is most specific, or both are equally specific
                if (fieldValue.matches(dimensionBinding.getValues())) {
                    visitor.acceptValue(name,
                                        fieldValue.getValue(),
                                        dimensionBinding,
                                        owner,
                                        fieldValue.getDimensionValues());
                }
                if (visitor.isDone()) return;
                fieldIndex++;
            }
            else { // Inherited is most specific at this point
                if (inheritedProfileValue.matches(dimensionBinding.getValues())) {
                    @SuppressWarnings("unchecked")
                    List<QueryProfile> inheritedProfileList = (List<QueryProfile>)inheritedProfileValue.getValue();
                    for (QueryProfile inheritedProfile : inheritedProfileList) {
                        if (visitor.visitInherited()) {
                            inheritedProfile.accept(allowContent,
                                                    visitor,
                                                    dimensionBinding.createFor(inheritedProfile.getDimensions()),
                                                    owner);
                        }
                        if (visitor.isDone()) return;
                    }
                }
                inheritedIndex++;
            }
            if (visitor.isDone()) return;
        }
    }

    void acceptAllValues(boolean allowContent,
                         QueryProfileVisitor visitor,
                         QueryProfileType type,
                         DimensionBinding dimensionBinding) {
        if ( ! frozen)
            Collections.sort(variants);
        for (QueryProfileVariant variant : variants) {
            if (variant.matches(dimensionBinding.getValues()))
                variant.accept(allowContent, type, visitor, dimensionBinding);
            if (visitor.isDone()) return;
        }
    }

    /**
     * Returns the most specific matching value of a name for a given set of <b>canonical</b> dimension values.
     *
     * @param name the name to return the best matching value of
     * @param dimensionBinding the dimension bindings to use in this
     */
    public Object get(String name, QueryProfileType type, boolean allowQueryProfileResult, DimensionBinding dimensionBinding) {
        SingleValueQueryProfileVisitor visitor = new SingleValueQueryProfileVisitor(Collections.singletonList(name),allowQueryProfileResult);
        visitor.enter("");
        accept(true, type, visitor, dimensionBinding);
        visitor.leave("");
        return visitor.getResult();
    }

    /** Inherits a particular profile in a variant of this */
    public void inherit(QueryProfile profile, DimensionValues dimensionValues) {
        ensureNotFrozen();

        // Update variant
        getVariant(dimensionValues,true).inherit(profile);

        // Update per-variable optimized structure
        @SuppressWarnings("unchecked")
        List<QueryProfile> inheritedAtDimensionValues = (List<QueryProfile>)inheritedProfiles.getExact(dimensionValues);
        if (inheritedAtDimensionValues == null) {
            inheritedAtDimensionValues = new ArrayList<>();
            inheritedProfiles.put(dimensionValues, inheritedAtDimensionValues);
        }
        inheritedAtDimensionValues.add(profile);
    }

    /**
     * Sets a value to this
     *
     * @param fieldName the name of the field to set. This cannot be a compound (dotted) name
     * @param dimensionValues the dimension values for which this value applies
     * @param value the value to set
     */
    public void set(String fieldName, DimensionValues dimensionValues, Object value) {
        ensureNotFrozen();

        // Update variant
        Object combinedValue = getVariant(dimensionValues, true).set(fieldName, value);

        // Update per-variable optimized structure
        FieldValues fieldValues = fieldValuesByName.get(fieldName);
        if (fieldValues == null) {
            fieldValues = new FieldValues();
            fieldValuesByName.put(fieldName, fieldValues);
        }

        if (combinedValue != null)
            fieldValues.put(dimensionValues, combinedValue);
    }

    /**
     * Makes a value unoverridable in a given context.
     */
    public void setOverridable(String fieldName, boolean overridable, DimensionValues dimensionValues) {
        getVariant(dimensionValues, true).setOverridable(fieldName, overridable);
    }

    public Boolean isOverridable(String fieldName, DimensionValues dimensionValues) {
        QueryProfileVariant variant = getVariant(dimensionValues, false);
        if (variant == null) return null;
        return variant.isOverridable(fieldName);
    }

    /**
     * Returns the dimensions over which the virtual profiles in this may return different values.
     * Each dimension is a name for which a key-value may be supplied in the context properties
     * on lookup time to influence the value returned.
     * The dimensions may not be modified - the returned list is always read only.
     */
    // Note: A performance optimization in DimensionBinding depends on the identity of the list returned from this
    public List<String> getDimensions() { return dimensions; }

    /** Returns the map of field values of this indexed by field name. */
    public Map<String, FieldValues> getFieldValues() { return fieldValuesByName; }

    /** Returns the profiles inherited from various variants of this */
    public FieldValues getInherited() { return inheritedProfiles; }

    /**
     * Returns all the variants of this, sorted by specificity. This is content as declared.
     * The returned list is always unmodifiable.
     */
    public List<QueryProfileVariant> getVariants() {
        if (frozen) return variants; // Already unmodifiable
        return Collections.unmodifiableList(variants);
    }

    @Override
    public QueryProfileVariants clone() {
        try {
            if (frozen) return this;
            QueryProfileVariants clone = (QueryProfileVariants)super.clone();
            clone.inheritedProfiles = inheritedProfiles.clone();

            clone.variants = new ArrayList<>();
            for (QueryProfileVariant variant : variants)
                clone.variants.add(variant.clone());

            clone.fieldValuesByName = new HashMap<>();
            for (Map.Entry<String, FieldValues> entry : fieldValuesByName.entrySet())
                clone.fieldValuesByName.put(entry.getKey(), entry.getValue().clone(entry.getKey(), clone.variants));

            return clone;
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Throws an IllegalStateException if this is frozen */
    protected void ensureNotFrozen() {
        if (frozen)
            throw new IllegalStateException(this + " is frozen and cannot be modified");
    }

    /**
     * Returns the query profile variant having exactly the given dimensions, and creates it if create is set and
     * it is missing
     *
     * @param  dimensionValues the dimension values
     * @param  create whether or not to create the variant if missing
     * @return the profile variant, or null if not found and create is false
     */
    public QueryProfileVariant getVariant(DimensionValues dimensionValues, boolean create) {
        for (QueryProfileVariant profileVariant : variants)
            if (profileVariant.getDimensionValues().equals(dimensionValues))
                return profileVariant;

        // Not found
        if ( ! create) return null;
        QueryProfileVariant variant = new QueryProfileVariant(dimensionValues, owner);
        variants.add(variant);
        return variant;
    }

    @Override
    public String toString() { return "variants of " + owner; }

    public static class FieldValues implements Freezable, Cloneable {

        private List<FieldValue> resolutionList = null;

        private boolean frozen = false;

        @Override
        public void freeze() {
            if (frozen) return;
            sort();
            if (resolutionList != null)
                resolutionList = ImmutableList.copyOf(resolutionList);
            frozen = true;
        }

        @Override
        public boolean isFrozen() {
            return frozen;
        }

        public void put(DimensionValues dimensionValues, Object value) {
            ensureNotFrozen();
            if (resolutionList == null) resolutionList = new ArrayList<>();
            FieldValue fieldValue = getExactFieldValue(dimensionValues);
            if (fieldValue != null) // Replace
                fieldValue.setValue(value);
            else
                resolutionList.add(new FieldValue(dimensionValues, value));
        }

        /** Returns the value having exactly the given dimensions, or null if none */
        public Object getExact(DimensionValues dimensionValues) {
            FieldValue value = getExactFieldValue(dimensionValues);
            if (value == null) return null;
            return value.getValue();
        }

        /** Returns the field value having exactly the given dimensions, or null if none */
        private FieldValue getExactFieldValue(DimensionValues dimensionValues) {
            for (FieldValue fieldValue : asList())
                if (fieldValue.getDimensionValues().equals(dimensionValues))
                    return fieldValue;
            return null;
        }

        /** Returns the field values (values for various dimensions) for this field as a read-only list (never null) */
        public List<FieldValue> asList() {
            if (resolutionList == null) return Collections.emptyList();
            return resolutionList;
        }

        public FieldValue getIfExists(int index) {
            if (index >= size()) return null;
            return resolutionList.get(index);
        }

        public void sort() {
            if (frozen) return ; // sorted already
            if (resolutionList != null)
                Collections.sort(resolutionList);
        }

        /** Same as asList().size() */
        public int size() {
            if (resolutionList == null) return 0;
            return resolutionList.size();
        }

        /** Throws an IllegalStateException if this is frozen */
        protected void ensureNotFrozen() {
            if (frozen)
                throw new IllegalStateException(this + " is frozen and cannot be modified");
        }

        /** Clone by filling in values from the given variants */
        public FieldValues clone(String fieldName,List<QueryProfileVariant> clonedVariants) {
            try {
                if (frozen) return this;
                FieldValues clone = (FieldValues)super.clone();

                if (resolutionList != null) {
                    clone.resolutionList = new ArrayList<>(resolutionList.size());
                    for (FieldValue value : resolutionList)
                        clone.resolutionList.add(value.clone(fieldName, clonedVariants));
                }

                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public FieldValues clone() {
            try {
                if (frozen) return this;
                FieldValues clone = (FieldValues)super.clone();

                if (resolutionList != null) {
                    clone.resolutionList = new ArrayList<>(resolutionList.size());
                    for (FieldValue value : resolutionList)
                        clone.resolutionList.add(value.clone());
                }

                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public static class FieldValue implements Comparable<FieldValue>, Cloneable {

        private final DimensionValues dimensionValues;
        private Object value;

        public FieldValue(DimensionValues dimensionValues, Object value) {
            this.dimensionValues = dimensionValues;
            this.value = value;
        }

        /**
         * Returns the dimension values for which this value should be used.
         * The dimension array is always of the exact size of the dimensions specified by the owning QueryProfileVariants,
         * and the values appear in the order defined. "Wildcard" dimensions are represented by a null.
         */
        public DimensionValues getDimensionValues() { return dimensionValues; }

        /** Returns the value to use for this set of dimension values */
        public Object getValue() { return value; }

        /** Sets the value to use for this set of dimension values */
        public void setValue(Object value) { this.value = value; }

        public boolean matches(DimensionValues givenDimensionValues) {
            return dimensionValues.matches(givenDimensionValues);
        }

        /**
         * Implements the sort order of this which is based on specificity
         * where dimensions to the left are more significant.
         * <p>
         * <b>Note:</b> This ordering is not consistent with equals - it returns 0 when the same dimensions
         * are <i>set</i>, regardless of what they are set <i>to</i>.
         */
        @Override
        public int compareTo(FieldValue other) {
            return this.dimensionValues.compareTo(other.dimensionValues);
        }

        /** Clone by filling in the value from the given variants */
        public FieldValue clone(String fieldName, List<QueryProfileVariant> clonedVariants) {
            try {
                FieldValue clone = (FieldValue)super.clone();
                if (this.value instanceof QueryProfile)
                    clone.value = lookupInVariants(fieldName, dimensionValues, clonedVariants);
                // Otherwise the value is immutable, so keep it as-is
                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public FieldValue clone() {
            try {
                FieldValue clone = (FieldValue)super.clone();
                clone.value = QueryProfile.cloneIfNecessary(this.value);
                return clone;
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }

        private Object lookupInVariants(String fieldName, DimensionValues dimensionValues, List<QueryProfileVariant> variants) {
            for (QueryProfileVariant variant : variants) {
                if ( ! variant.getDimensionValues().equals(dimensionValues)) continue;
                return variant.values().get(fieldName);
            }
            return null;
        }

        @Override
        public String toString() { return "field value " + value + " for " + dimensionValues; }

    }

}
