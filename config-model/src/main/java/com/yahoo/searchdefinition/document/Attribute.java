// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.TensorType;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A search-time document attribute (per-document in-memory value).
 * This belongs to the field defining the attribute.
 *
 * @author  bratseth
 */
public final class Attribute implements Cloneable, Serializable {

    // Remember to change hashCode and equals when you add new fields

    private String name;

    private Type type;
    private CollectionType collectionType;

    private boolean removeIfZero = false;
    private boolean createIfNonExistent = false;
    private boolean enableBitVectors = false;
    private boolean enableOnlyBitVector = false;

    private boolean fastSearch = false;
    private boolean fastAccess = false;
    private boolean huge = false;
    private int arity = BooleanIndexDefinition.DEFAULT_ARITY;
    private long lowerBound = BooleanIndexDefinition.DEFAULT_LOWER_BOUND;
    private long upperBound = BooleanIndexDefinition.DEFAULT_UPPER_BOUND;
    private double densePostingListThreshold = BooleanIndexDefinition.DEFAULT_DENSE_POSTING_LIST_THRESHOLD;

    /** This is set if the type of this is TENSOR */
    private Optional<TensorType> tensorType = Optional.empty();

    /** This is set if the type of this is REFERENCE */
    private final Optional<StructuredDataType> referenceDocumentType;

    private boolean isPosition = false;
    private final Sorting sorting = new Sorting();

    /** The aliases for this attribute */
    private final Set<String> aliases = new LinkedHashSet<>();

    /**
     * True if this attribute should be returned during first pass of search.
     * Null means make the default decision for this kind of attribute
     */
    private Boolean prefetch = null;

    /** The attribute type enumeration */
    public enum Type {
        BYTE("byte", "INT8"),
        SHORT("short", "INT16"),
        INTEGER("integer", "INT32"),
        LONG("long", "INT64"),
        FLOAT("float", "FLOAT"),
        DOUBLE("double", "DOUBLE"),
        STRING("string", "STRING"),
        PREDICATE("predicate", "PREDICATE"),
        TENSOR("tensor", "TENSOR"),
        REFERENCE("reference", "REFERENCE");

        private final String myName;  // different from what name() returns.
        private final String exportAttributeTypeName;

        private Type(String name, String exportAttributeTypeName) {
            this.myName=name;
            this.exportAttributeTypeName = exportAttributeTypeName;
        }

        public String getName() { return myName; }
        public String getExportAttributeTypeName() { return exportAttributeTypeName; }

        public String toString() {
            return "type: " + myName;
        }
    }

    /** The attribute collection type enumeration */
    public enum CollectionType  {

        SINGLE("SINGLE"),
        ARRAY("ARRAY"),
        WEIGHTEDSET ("WEIGHTEDSET");

        private final String name;

        private CollectionType(String name) {
            this.name=name;
        }

        public String getName() { return name; }

        public String toString() {
            return "collectiontype: " + name;
        }
    }

    /** Creates an attribute with default settings */
    public Attribute(String name, DataType fieldType) {
        this(name, convertDataType(fieldType), convertCollectionType(fieldType), convertTensorType(fieldType), convertTargetType(fieldType));
        setRemoveIfZero(fieldType instanceof WeightedSetDataType ? ((WeightedSetDataType)fieldType).removeIfZero() : false);
        setCreateIfNonExistent(fieldType instanceof WeightedSetDataType ? ((WeightedSetDataType)fieldType).createIfNonExistent() : false);
    }

    public Attribute(String name, Type type, CollectionType collectionType) {
        this(name, type, collectionType, Optional.empty(), Optional.empty());
    }

    public Attribute(String name,
                     Type type,
                     CollectionType collectionType,
                     Optional<TensorType> tensorType,
                     Optional<StructuredDataType> referenceDocumentType) {
        this.name=name;
        setType(type);
        setCollectionType(collectionType);
        this.tensorType = tensorType;
        this.referenceDocumentType = referenceDocumentType;
    }

    public Attribute convertToArray() {
        Attribute result = clone();
        result.collectionType = CollectionType.ARRAY;
        return result;
    }

    /**
     * <p>Returns whether this attribute should be included in the "attributeprefetch" summary
     * which is returned to the Qrs by prefetchAttributes, used by blending, uniquing etc.
     *
     * <p>Single value attributes are prefetched by default if summary is true.
     * Multi value attributes are not.</p>
     */
    public boolean isPrefetch() {
        if (prefetch!=null) return prefetch.booleanValue();

        if (CollectionType.SINGLE.equals(collectionType)) {
            return true;
        }

        return false;
    }

    /** Returns the prefetch value of this, null if the default is used. */
    public Boolean getPrefetchValue() { return prefetch; }

    public boolean isRemoveIfZero()       { return removeIfZero; }
    public boolean isCreateIfNonExistent(){ return createIfNonExistent; }
    public boolean isEnabledBitVectors()  { return enableBitVectors; }
    public boolean isEnabledOnlyBitVector() { return enableOnlyBitVector; }
    public boolean isFastSearch()         { return fastSearch; }
    public boolean isFastAccess()         { return fastAccess; }
    public boolean isHuge()               { return huge; }
    public boolean isPosition()           { return isPosition; }

    public int arity() { return arity; }
    public long lowerBound() { return lowerBound; }
    public long upperBound() { return upperBound; }
    public double densePostingListThreshold() { return densePostingListThreshold; }
    public Optional<TensorType> tensorType() { return tensorType; }
    public Optional<StructuredDataType> referenceDocumentType() { return referenceDocumentType; }

    public Sorting getSorting() { return sorting; }

    public void setRemoveIfZero(boolean remove)                  { this.removeIfZero = remove; }
    public void setCreateIfNonExistent(boolean create)           { this.createIfNonExistent = create; }

    /**
     * Sets whether this should be included in the "attributeprefetch" document summary.
     * True or false to override default, null to use default
     */
    public void setPrefetch(Boolean prefetch)                    { this.prefetch = prefetch; }
    public void setEnableBitVectors(boolean enableBitVectors)    { this.enableBitVectors = enableBitVectors; }
    public void setEnableOnlyBitVector(boolean enableOnlyBitVector) { this.enableOnlyBitVector = enableOnlyBitVector; }
    public void setFastSearch(boolean fastSearch)                { this.fastSearch = fastSearch; }
    public void setHuge(boolean huge)                            { this.huge = huge; }
    public void setFastAccess(boolean fastAccess)                { this.fastAccess = fastAccess; }
    public void setPosition(boolean position)                    { this.isPosition = position; }
    public void setArity(int arity)                              { this.arity = arity; }
    public void setLowerBound(long lowerBound)                   { this.lowerBound = lowerBound; }
    public void setUpperBound(long upperBound)                   { this.upperBound = upperBound; }
    public void setDensePostingListThreshold(double threshold)   { this.densePostingListThreshold = threshold; }
    public void setTensorType(TensorType tensorType)             { this.tensorType = Optional.of(tensorType); }

    public String         getName()                     { return name; }
    public Type           getType()                     { return type; }
    public CollectionType getCollectionType()           { return collectionType; }

    public void  setName(String name)                   { this.name=name; }
    private void setType(Type type)                     { this.type=type; }
    public void  setCollectionType(CollectionType type) { this.collectionType=type; }

    /** Converts to the right attribute type from a field datatype */
    public static Type convertDataType(DataType fieldType) {
        FieldValue fval = fieldType.createFieldValue();
        if (fval instanceof StringFieldValue) {
            return Type.STRING;
        } else if (fval instanceof IntegerFieldValue) {
            return Type.INTEGER;
        } else if (fval instanceof LongFieldValue) {
            return Type.LONG;
        } else if (fval instanceof FloatFieldValue) {
            return Type.FLOAT;
        } else if (fval instanceof DoubleFieldValue) {
            return Type.DOUBLE;
        } else if (fval instanceof ByteFieldValue) {
            return Type.BYTE;
        } else if (fval instanceof Raw) {
            return Type.BYTE;
        } else if (fval instanceof PredicateFieldValue) {
            return Type.PREDICATE;
        } else if (fval instanceof TensorFieldValue) {
            return Type.TENSOR;
        } else if (fieldType instanceof CollectionDataType) {
            return convertDataType(((CollectionDataType) fieldType).getNestedType());
        } else if (fieldType instanceof ReferenceDataType) {
            return Type.REFERENCE;
        } else {
            throw new IllegalArgumentException("Don't know which attribute type to " +
                                               "convert " + fieldType + " to");
        }
    }

    /** Converts to the right attribute type from a field datatype */
    private static CollectionType convertCollectionType(DataType fieldType) {
        if (fieldType instanceof ArrayDataType) {
            return CollectionType.ARRAY;
        } else if (fieldType instanceof WeightedSetDataType) {
            return CollectionType.WEIGHTEDSET;
        } else if (fieldType instanceof TensorDataType) {
            return CollectionType.SINGLE;
        } else if (fieldType instanceof PrimitiveDataType) {
            return CollectionType.SINGLE;
        } else if (fieldType instanceof ReferenceDataType) {
            return CollectionType.SINGLE;
        } else {
            throw new IllegalArgumentException("Field " + fieldType + " not supported in convertCollectionType");
        }
    }

    private static Optional<TensorType> convertTensorType(DataType fieldType) {
        if ( ! ( fieldType instanceof TensorDataType)) return Optional.empty();
        return Optional.of(((TensorDataType)fieldType).getTensorType());
    }

    private static Optional<StructuredDataType> convertTargetType(DataType fieldType) {
        return Optional.of(fieldType)
                .filter(ReferenceDataType.class::isInstance)
                .map(ReferenceDataType.class::cast)
                .map(ReferenceDataType::getTargetType);
    }

    /** Converts to the right field type from an attribute type */
    private DataType toDataType(Type attributeType) {
        switch (attributeType) {
            case STRING : return DataType.STRING;
            case INTEGER: return DataType.INT;
            case LONG: return DataType.LONG;
            case FLOAT: return DataType.FLOAT;
            case DOUBLE: return DataType.DOUBLE;
            case BYTE: return DataType.BYTE;
            case PREDICATE: return DataType.PREDICATE;
            case TENSOR: return DataType.getTensor(tensorType.orElseThrow(IllegalStateException::new));
            case REFERENCE: return createReferenceDataType();
            default: throw new IllegalArgumentException("Unknown attribute type " + attributeType);
        }
    }

    private DataType createReferenceDataType() {
        if (!referenceDocumentType.isPresent()) {
            throw new IllegalStateException("Referenced document type is not set!");
        }
        StructuredDataType type = referenceDocumentType.get();
        if (type instanceof DocumentType) {
            return ReferenceDataType.createWithInferredId((DocumentType) type);
        } else {
            return ReferenceDataType.createWithInferredId((TemporaryStructuredDataType) type);
        }
    }

    public DataType getDataType() {
        DataType dataType = toDataType(type);
        if (collectionType.equals(Attribute.CollectionType.ARRAY)) {
            return DataType.getArray(dataType);
        } else if (collectionType.equals(Attribute.CollectionType.WEIGHTEDSET)) {
            return DataType.getWeightedSet(dataType, createIfNonExistent, removeIfZero);
        } else {
            return dataType;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name, type, collectionType, sorting, isPrefetch(), fastAccess, removeIfZero, createIfNonExistent,
                isPosition, huge, enableBitVectors, enableOnlyBitVector, tensorType, referenceDocumentType);
    }

    @Override
    public boolean equals(Object object) {
        if (! (object instanceof Attribute)) return false;

        Attribute other=(Attribute)object;
        if (!this.name.equals(other.name)) return false;
        return isCompatible(other);
    }

    /** Returns whether these attributes describes the same entity, even if they have different names */
    public boolean isCompatible(Attribute other) {
        if ( ! this.type.equals(other.type)) return false;
        if ( ! this.collectionType.equals(other.collectionType)) return false;
        if (this.isPrefetch() != other.isPrefetch()) return false;
        if (this.removeIfZero != other.removeIfZero) return false;
        if (this.createIfNonExistent != other.createIfNonExistent) return false;
        if (this.enableBitVectors != other.enableBitVectors) return false;
        if (this.enableOnlyBitVector != other.enableOnlyBitVector) return false;
        // if (this.noSearch != other.noSearch) return false; No backend consequences so compatible for now
        if (this.fastSearch != other.fastSearch) return false;
        if (this.huge != other.huge) return false;
        if ( ! this.sorting.equals(other.sorting)) return false;
        if (!this.tensorType.equals(other.tensorType)) return false;
        if (!this.referenceDocumentType.equals(other.referenceDocumentType)) return false;

        return true;
    }

    @Override
    public Attribute clone() {
        try {
            return (Attribute)super.clone();
        }
        catch (CloneNotSupportedException e) {
            throw new RuntimeException("Programming error");
        }
    }

    public String toString() {
        return "attribute '" + name + "' (" + type + ")";
    }

    public Set<String> getAliases() {
        return aliases;
    }

}
