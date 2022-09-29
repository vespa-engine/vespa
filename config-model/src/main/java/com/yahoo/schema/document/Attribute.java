// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.CollectionDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.DocumentType;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.document.StructuredDataType;
import com.yahoo.document.TensorDataType;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.datatypes.BoolFieldValue;
import com.yahoo.document.datatypes.ByteFieldValue;
import com.yahoo.document.datatypes.DoubleFieldValue;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.LongFieldValue;
import com.yahoo.document.datatypes.PredicateFieldValue;
import com.yahoo.document.datatypes.Raw;
import com.yahoo.document.datatypes.Float16FieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.TensorFieldValue;
import com.yahoo.tensor.TensorType;

import java.io.Serializable;
import java.util.function.Supplier;
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

    public enum DistanceMetric { EUCLIDEAN, ANGULAR, GEODEGREES, INNERPRODUCT, HAMMING }

    // Remember to change hashCode and equals when you add new fields

    private String name;

    private Type type;
    private CollectionType collectionType;

    private boolean removeIfZero = false;
    private boolean createIfNonExistent = false;
    private boolean enableOnlyBitVector = false;

    private boolean fastRank = false;
    private boolean fastSearch = false;
    private boolean fastAccess = false;
    private boolean mutable = false;
    private boolean paged = false;
    private int arity = BooleanIndexDefinition.DEFAULT_ARITY;
    private long lowerBound = BooleanIndexDefinition.DEFAULT_LOWER_BOUND;
    private long upperBound = BooleanIndexDefinition.DEFAULT_UPPER_BOUND;
    private double densePostingListThreshold = BooleanIndexDefinition.DEFAULT_DENSE_POSTING_LIST_THRESHOLD;

    /** This is set if the type of this is TENSOR */
    private Optional<TensorType> tensorType = Optional.empty();

    /** This is set if the type of this is REFERENCE */
    private final Optional<StructuredDataType> referenceDocumentType;

    private Optional<DistanceMetric> distanceMetric = Optional.empty();

    private Optional<HnswIndexParams> hnswIndexParams = Optional.empty();

    private boolean isPosition = false;
    private final Sorting sorting = new Sorting();

    /** The aliases for this attribute */
    private final Set<String> aliases = new LinkedHashSet<>();

    private Dictionary dictionary = null;
    private Case casing = Case.UNCASED;

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
        FLOAT16("float16", "FLOAT16"),
        FLOAT("float", "FLOAT"),
        DOUBLE("double", "DOUBLE"),
        STRING("string", "STRING"),
        BOOL("bool", "BOOL"),
        PREDICATE("predicate", "PREDICATE"),
        TENSOR("tensor", "TENSOR"),
        REFERENCE("reference", "REFERENCE");

        private final String myName;  // different from what name() returns.
        private final String exportAttributeTypeName;

        Type(String name, String exportAttributeTypeName) {
            this.myName=name;
            this.exportAttributeTypeName = exportAttributeTypeName;
        }

        public String getName() { return myName; }
        public String getExportAttributeTypeName() { return exportAttributeTypeName; }

        @Override
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

        CollectionType(String name) {
            this.name=name;
        }

        public String getName() { return name; }

        @Override
        public String toString() {
            return "collectiontype: " + name;
        }

    }

    /** Creates an attribute with default settings */
    public Attribute(String name, DataType fieldType) {
        this(name, convertDataType(fieldType), convertCollectionType(fieldType), convertTensorType(fieldType), convertTargetType(fieldType));
        setRemoveIfZero(fieldType instanceof WeightedSetDataType wsdt && wsdt.removeIfZero());
        setCreateIfNonExistent(fieldType instanceof WeightedSetDataType wsdt && wsdt.createIfNonExistent());
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
        if (prefetch!=null) return prefetch;

        if (tensorType.isPresent()) {
            return false;
        }
        if (CollectionType.SINGLE.equals(collectionType)) {
            return true;
        }

        return false;
    }

    /** Returns the prefetch value of this, null if the default is used. */
    public Boolean getPrefetchValue() { return prefetch; }

    public boolean isRemoveIfZero()         { return removeIfZero; }
    public boolean isCreateIfNonExistent()  { return createIfNonExistent; }
    public boolean isEnabledOnlyBitVector() { return enableOnlyBitVector; }
    public boolean isFastSearch()           { return fastSearch; }
    public boolean isFastRank()            {  return fastRank; }
    public boolean isFastAccess()           { return fastAccess; }
    public boolean isPaged()                { return paged; }
    public boolean isPosition()             { return isPosition; }
    public boolean isMutable()              { return mutable; }

    public int arity()       { return arity; }
    public long lowerBound() { return lowerBound; }
    public long upperBound() { return upperBound; }
    public double densePostingListThreshold() { return densePostingListThreshold; }
    public Optional<TensorType> tensorType()  { return tensorType; }
    public Optional<StructuredDataType> referenceDocumentType() { return referenceDocumentType; }

    public static final DistanceMetric DEFAULT_DISTANCE_METRIC = DistanceMetric.EUCLIDEAN;
    public DistanceMetric distanceMetric() {
        return distanceMetric.orElse(DEFAULT_DISTANCE_METRIC);
    }
    public Optional<HnswIndexParams> hnswIndexParams() { return hnswIndexParams; }

    public Sorting getSorting() { return sorting; }
    public Dictionary getDictionary() { return dictionary; }
    public Case getCase() { return casing; }

    public void setRemoveIfZero(boolean remove)                  { this.removeIfZero = remove; }
    public void setCreateIfNonExistent(boolean create)           { this.createIfNonExistent = create; }

    /**
     * Sets whether this should be included in the "attributeprefetch" document summary.
     * True or false to override default, null to use default
     */
    public void setPrefetch(Boolean prefetch)                    { this.prefetch = prefetch; }
    public void setEnableOnlyBitVector(boolean enableOnlyBitVector) { this.enableOnlyBitVector = enableOnlyBitVector; }
    public void setFastRank(boolean value) {
        Supplier<IllegalArgumentException> badGen = () ->
                new IllegalArgumentException("The " + toString() + " does not support 'fast-rank'. " +
                        "Only supported for tensor types with at least one mapped dimension");
        var tt = tensorType.orElseThrow(badGen);
        for (var dim : tt.dimensions()) {
            if (dim.isMapped()) {
                this.fastRank = value;
                return;
            }
        }
        throw badGen.get();
    }
    public void setFastSearch(boolean fastSearch)                { this.fastSearch = fastSearch; }
    public void setPaged(boolean paged)                          { this.paged = paged; }
    public void setFastAccess(boolean fastAccess)                { this.fastAccess = fastAccess; }
    public void setPosition(boolean position)                    { this.isPosition = position; }
    public void setMutable(boolean mutable)                      { this.mutable = mutable; }
    public void setArity(int arity)                              { this.arity = arity; }
    public void setLowerBound(long lowerBound)                   { this.lowerBound = lowerBound; }
    public void setUpperBound(long upperBound)                   { this.upperBound = upperBound; }
    public void setDensePostingListThreshold(double threshold)   { this.densePostingListThreshold = threshold; }
    public void setTensorType(TensorType tensorType)             { this.tensorType = Optional.of(tensorType); }
    public void setDistanceMetric(DistanceMetric metric)         { this.distanceMetric = Optional.of(metric); }
    public void setHnswIndexParams(HnswIndexParams params)       { this.hnswIndexParams = Optional.of(params); }
    public void setDictionary(Dictionary dictionary)             { this.dictionary = dictionary; }
    public void setCase(Case casing)                             { this.casing = casing; }

    public String         getName()                     { return name; }
    public Type           getType()                     { return type; }
    public CollectionType getCollectionType()           { return collectionType; }

    public void  setName(String name)                   { this.name=name; }
    private void setType(Type type)                     { this.type=type; }
    public void  setCollectionType(CollectionType type) { this.collectionType=type; }

    /** Converts to the right attribute type from a field datatype */
    public static Type convertDataType(DataType fieldType) {
        if (fieldType instanceof NewDocumentReferenceDataType) {
            return Type.REFERENCE;
        } else if (fieldType instanceof CollectionDataType) {
            return convertDataType(((CollectionDataType) fieldType).getNestedType());
        }
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
        } else if (fval instanceof BoolFieldValue) {
            return Type.BOOL;
        } else if (fval instanceof Float16FieldValue) {
            return Type.FLOAT16;
        } else if (fval instanceof ByteFieldValue) {
            return Type.BYTE;
        } else if (fval instanceof Raw) {
            return Type.BYTE;
        } else if (fval instanceof PredicateFieldValue) {
            return Type.PREDICATE;
        } else if (fval instanceof TensorFieldValue) {
            return Type.TENSOR;
        } else {
            throw new IllegalArgumentException("Don't know which attribute type to convert "
                                               + fieldType + " [" + fieldType.getClass() + "] to");
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
        } else if (fieldType instanceof NewDocumentReferenceDataType) {
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
                .filter(NewDocumentReferenceDataType.class::isInstance)
                .map(NewDocumentReferenceDataType.class::cast)
                .map(NewDocumentReferenceDataType::getTargetType);
    }

    /** Converts to the right field type from an attribute type */
    private DataType toDataType(Type attributeType) {
        return switch (attributeType) {
            case STRING -> DataType.STRING;
            case INTEGER -> DataType.INT;
            case LONG -> DataType.LONG;
            case FLOAT16 -> DataType.FLOAT16;
            case FLOAT -> DataType.FLOAT;
            case DOUBLE -> DataType.DOUBLE;
            case BOOL -> DataType.BOOL;
            case BYTE -> DataType.BYTE;
            case PREDICATE -> DataType.PREDICATE;
            case TENSOR -> DataType.getTensor(tensorType.orElseThrow(IllegalStateException::new));
            case REFERENCE-> createReferenceDataType();
            default -> throw new IllegalArgumentException("Unknown attribute type " + attributeType);
        };
    }

    private DataType createReferenceDataType() {
        if (referenceDocumentType.isEmpty()) {
            throw new IllegalStateException("Referenced document type is not set");
        }
        StructuredDataType type = referenceDocumentType.get();
        if (type instanceof DocumentType) {
            return new NewDocumentReferenceDataType((DocumentType) type);
        } else {
            return NewDocumentReferenceDataType.forDocumentName(type.getName());
        }
    }

    public DataType getDataType() {
        DataType dataType = toDataType(type);
        if (collectionType == Attribute.CollectionType.ARRAY) {
            return DataType.getArray(dataType);
        } else if (collectionType == Attribute.CollectionType.WEIGHTEDSET) {
            return DataType.getWeightedSet(dataType, createIfNonExistent, removeIfZero);
        } else {
            return dataType;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                name, type, collectionType, sorting, dictionary, isPrefetch(), fastAccess, removeIfZero,
                createIfNonExistent, isPosition, mutable, paged, enableOnlyBitVector,
                tensorType, referenceDocumentType, distanceMetric, hnswIndexParams);
    }

    @Override
    public boolean equals(Object object) {
        if (! (object instanceof Attribute other)) return false;

        if (!this.name.equals(other.name)) return false;
        return isCompatible(other);
    }

    /** Returns whether these attributes describes the same entity, even if they have different names */
    public boolean isCompatible(Attribute other) {
        if (! this.type.equals(other.type)) return false;
        if (! this.collectionType.equals(other.collectionType)) return false;
        if (this.isPrefetch() != other.isPrefetch()) return false;
        if (this.removeIfZero != other.removeIfZero) return false;
        if (this.createIfNonExistent != other.createIfNonExistent) return false;
        if (this.enableOnlyBitVector != other.enableOnlyBitVector) return false;
        if (this.fastSearch != other.fastSearch) return false;
        if (this.mutable != other.mutable) return false;
        if (this.paged != other.paged) return false;
        if (! this.sorting.equals(other.sorting)) return false;
        if (! Objects.equals(dictionary, other.dictionary)) return false;
        if (! Objects.equals(tensorType, other.tensorType)) return false;
        if (! Objects.equals(referenceDocumentType, other.referenceDocumentType)) return false;
        if (! Objects.equals(distanceMetric, other.distanceMetric)) return false;
        if (! Objects.equals(hnswIndexParams, other.hnswIndexParams)) return false;

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

    @Override
    public String toString() {
        return "attribute '" + name + "' (" + (tensorType.isPresent() ? tensorType.get() : type) + ")";
    }

    public Set<String> getAliases() {
        return aliases;
    }

}
