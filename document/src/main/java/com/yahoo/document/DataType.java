// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.collections.Pair;
import com.yahoo.concurrent.CopyOnWriteHashMap;
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
import com.yahoo.document.datatypes.UriFieldValue;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.objects.Identifiable;
import com.yahoo.vespa.objects.Ids;
import com.yahoo.vespa.objects.ObjectVisitor;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

/**
 * Enumeration of the possible types of fields. Since arrays and weighted sets may be defined for any types, including
 * themselves, this enumeration is open ended.
 *
 * @author bratseth
 */
public abstract class DataType extends Identifiable implements Serializable, Comparable<DataType> {

    // The global class identifier shared with C++.
    public static int classId = registerClass(Ids.document + 50, DataType.class);

    // NOTE: These types are also defined in
    // document/src/vespa/document/datatype/datatype.h
    // Changes here must also be done there
    public final static NumericDataType NONE = new NumericDataType("none", -1, IntegerFieldValue.class, IntegerFieldValue.getFactory());
    public final static NumericDataType INT = new NumericDataType("int", 0, IntegerFieldValue.class, IntegerFieldValue.getFactory());
    public final static NumericDataType FLOAT = new NumericDataType("float", 1, FloatFieldValue.class, FloatFieldValue.getFactory());
    public final static PrimitiveDataType STRING = new PrimitiveDataType("string", 2, StringFieldValue.class, StringFieldValue.getFactory());
    public final static PrimitiveDataType RAW = new PrimitiveDataType("raw", 3, Raw.class, Raw.getFactory());
    public final static NumericDataType LONG = new NumericDataType("long", 4, LongFieldValue.class, LongFieldValue.getFactory());
    public final static NumericDataType DOUBLE = new NumericDataType("double", 5, DoubleFieldValue.class, DoubleFieldValue.getFactory());
    public final static PrimitiveDataType BOOL = new PrimitiveDataType("bool", 6, BoolFieldValue.class, BoolFieldValue.getFactory());
    public final static NumericDataType FLOAT16 = new NumericDataType("float16", 7, Float16FieldValue.class, Float16FieldValue.getFactory());
    public final static DocumentType DOCUMENT = new DocumentType("document");
    public final static PrimitiveDataType URI = new PrimitiveDataType("uri", 10, UriFieldValue.class, new UriFieldValue.Factory());
    public final static NumericDataType BYTE = new NumericDataType("byte", 16, ByteFieldValue.class, ByteFieldValue.getFactory());
    public final static PrimitiveDataType PREDICATE = new PrimitiveDataType("predicate", 20, PredicateFieldValue.class, PredicateFieldValue.getFactory());
    public final static int tensorDataTypeCode = 21; // All TensorDataType instances have id=21 but carries additional type information serialized separately
    // ADDITIONAL parametrized types added at runtime: map, struct, array, weighted set, annotation reference, tensor

    // Tags are converted to weightedset<string> when reading the search definition TODO: Remove it
    public final static WeightedSetDataType TAG = new WeightedSetDataType(DataType.STRING, true, true);

    public static int lastPredefinedDataTypeId() {
        return 21;
    }

    /** Set to true when this type is registered in a type manager. From that time we should refuse changes. */
    private boolean registered = false;

    private String name;

    /** The id of this type */
    private int dataTypeId;

    static final private CopyOnWriteHashMap<Pair, Constructor> constructorCache = new CopyOnWriteHashMap<>();

    /**
     * Creates a datatype
     *
     * @param name       the name of the type
     * @param dataTypeId the id of the type
     */
    protected DataType(java.lang.String name, int dataTypeId) {
        this.name = name;
        this.dataTypeId = dataTypeId;
    }

    @Override
    public DataType clone() {
        return (DataType)super.clone();
    }

    void setRegistered() {
        registered = true;
    }
    public boolean isRegistered() {
        return registered;
    }

    /** Creates a new, empty FieldValue of this type */
    public abstract FieldValue createFieldValue();

    /**
     * Creates a field value by reflection
     *
     * @param arg the value of the newly created field value
     * @return a fully constructed value
     */
    protected FieldValue createByReflection(Object arg) {
        Class<?> valClass = getValueClass();
        if (valClass != null) {
            Pair<Class<?>, Class<?>> key = new Pair<>(valClass, arg.getClass());
            Constructor<?> cstr = constructorCache.get(key);
            if (cstr == null) {
                if (!constructorCache.containsKey(key)) {
                    try {
                        cstr = valClass.getConstructor(key.getSecond());
                        constructorCache.put(key, cstr);
                    } catch (NoSuchMethodException e) {
                        constructorCache.put(key, null);
                    }
                }
            }
            try {
                return cstr != null ? (FieldValue)cstr.newInstance(arg) : null;
            } catch (ReflectiveOperationException e) {
                // Only rethrow exceptions coming from the underlying FieldValue constructor.
                if (e instanceof InvocationTargetException) {
                    throw new IllegalArgumentException(e.getCause().getMessage(), e.getCause());
                }
            }
        }
        return null;
    }

    /**
     * Creates a new FieldValue of this type, with the given value.
     *
     * @param arg the value of the new FieldValue
     * @return a new FieldValue of this type, with the given value
     */
    public FieldValue createFieldValue(Object arg) {
        if (arg == null) {
            return createFieldValue();
        }
        FieldValue val = createByReflection(arg);
        if (val == null) {
            val = createFieldValue();
            if (val != null) {
                val.assign(arg);
            }
        }
        return val;
    }

    public abstract Class getValueClass();

    public abstract boolean isValueCompatible(FieldValue value);

    public final boolean isAssignableFrom(DataType dataType) {
        // TODO: Reverse this so that isValueCompatible() uses this instead.
        return isValueCompatible(dataType.createFieldValue());
    }

    /**
     * Returns an array datatype, where the array elements are of the given type
     *
     * @param type the type to create an array of
     * @return the array data type
     */
    public static ArrayDataType getArray(DataType type) {
        return new ArrayDataType(type);
    }

    /**
     * Returns a map datatype
     *
     * @param key   the key type
     * @param value the value type
     * @return the map data type
     */
    public static MapDataType getMap(DataType key, DataType value) {
        return new MapDataType(key, value);
    }

    /**
     * Returns a weighted set datatype, where the elements are of the given type
     *
     * @param type the type to create a weighted set of
     * @return the weighted set data type
     */
    public static WeightedSetDataType getWeightedSet(DataType type) {
        return getWeightedSet(type, false, false);
    }

    /**
     * Returns a weighted set datatype, where the elements are of the given type, and which supports the properties
     * createIfNonExistent and removeIfZero
     *
     * @param type                the type to create a weighted set of
     * @param createIfNonExistent whether the type has createIfNonExistent
     * @param removeIfZero        whether the type has removeIfZero
     * @return the weighted set data type
     */
    public static WeightedSetDataType getWeightedSet(DataType type, boolean createIfNonExistent, boolean removeIfZero) {
        return new WeightedSetDataType(type, createIfNonExistent, removeIfZero);
    }

    /** Returns the given tensor type as a DataType */
    public static TensorDataType getTensor(TensorType type) {
        return new TensorDataType(type);
    }

    public String getName() {
        return name;
    }

    /**
     * Sets the name of this data type. WARNING! Do not use!
     *
     * @param name the name of this data type.
     */
    protected void setName(String name) {
        this.name = name;
    }

    public int getId() {
        return dataTypeId;
    }

    /**
     * Sets the ID of this data type. WARNING! Do not use!
     *
     * @param id the ID of this data type.
     */
    public void setId(int id) {
        dataTypeId = id;
    }

    /**
     * Registers this type in the given document manager.
     *
     * @param manager the DocumentTypeManager to register in.
     */
    public final void register(DocumentTypeManager manager) {
        register(manager, new LinkedList<>());
    }

    protected void register(DocumentTypeManager manager, List<DataType> seenTypes) {
        manager.registerSingleType(this);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return (other instanceof DataType) && (dataTypeId == ((DataType)other).dataTypeId);
    }

    @Override
    public String toString() {
        return "datatype " + name + " (code: " + dataTypeId + ")";
    }

    public int getCode() {
        return dataTypeId;
    }

    /**
     * Creates a field path from the given field path string.
     *
     * @param fieldPathString a string containing the field path
     * @return Returns a valid field path, parsed from the string
     */
    public FieldPath buildFieldPath(String fieldPathString) {
        if (fieldPathString.length() > 0) {
            throw new IllegalArgumentException("Datatype " + toString() +
                                               " does not support further recursive structure: " + fieldPathString);
        }
        return new FieldPath();
    }

    /**
     * Returns the primitive datatype associated with this datatype, i.e. the type itself if this is a
     * PrimitiveDataType, the nested type if this is a CollectionDataType or null for all other cases
     *
     * @return primitive data type, or null
     */
    public PrimitiveDataType getPrimitiveType() {
        return null;
    }

    @Override
    public void visitMembers(ObjectVisitor visitor) {
        super.visitMembers(visitor);
        visitor.visit("name", name);
        visitor.visit("id", dataTypeId);
    }

    /**
     * Utility function for parsing field paths.
     */
    static String skipDotInString(String remaining, int endPos) {
        if (remaining.length() < endPos + 2) {
            return "";
        } else if (remaining.charAt(endPos + 1) == '.') {
            return remaining.substring(endPos + 2);
        } else {
            return remaining.substring(endPos + 1);
        }
    }

    @Override
    public int compareTo(DataType dataType) {
        return Integer.valueOf(dataTypeId).compareTo(dataType.dataTypeId);
    }

    /** Returns whether this is a multivalue type, i.e either a CollectionDataType or a MapDataType */
    public boolean isMultivalue() { return false; }

}
