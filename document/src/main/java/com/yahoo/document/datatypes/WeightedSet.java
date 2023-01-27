// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.WeightedSetDataType;
import com.yahoo.document.MapDataType;
import com.yahoo.document.FieldPath;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlSerializationHelper;
import com.yahoo.document.serialization.XmlStream;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


/**
 * A weighted set, a unique set of keys with an associated integer weight. This class
 * uses an encapsulated Map (actually a LinkedHashMap) that associates each key
 * with its weight (value).
 *
 * @author Einar M R Rosenvinge
 */
public final class WeightedSet<K extends FieldValue> extends CollectionFieldValue<K> implements Map<K, Integer> {

    private MapFieldValue<K, IntegerFieldValue> map;

    /**
     * Creates a new WeightedSet.
     *
     * @param type the data type for the field that this weighted set is associated with
     */
    public WeightedSet(DataType type) {
        this(type, 1);
    }

    /**
     * Creates a new weighted set with a given initial capacity.
     *
     * @param initialCapacity the initial capacity to use for the encapsulated Map
     */
    public WeightedSet(DataType type, int initialCapacity) {
        super((WeightedSetDataType) type);
        clearAndReserve(initialCapacity);
    }

    @Override
    public WeightedSetDataType getDataType() {
        return (WeightedSetDataType) super.getDataType();
    }

    @Override
    public Iterator<K> fieldValueIterator() {
        return map.keySet().iterator();
    }

    @Override
    public void assign(Object o) {
        if (!checkAssign(o)) {
            return;
        }

        if (o instanceof WeightedSet) {
            WeightedSet wset = (WeightedSet) o;
            if (getDataType().equals(wset.getDataType())) {
                map.assign(wset.map);
            } else {
                throw new IllegalArgumentException("Cannot assign a weighted set of type " + wset.getDataType()
                                                   + " to a weighted set of type " + getDataType());
            }
        } else if (o instanceof Map) {
            map = new WeightedSetWrapper((Map)o, map.getDataType());
        } else {
            throw new IllegalArgumentException("Class " + o.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    @Override
    public WeightedSet clone() {
        WeightedSet<K> newSet = (WeightedSet<K>) super.clone();
        newSet.map = (MapFieldValue<K, IntegerFieldValue>) map.clone();
        return newSet;
    }


    @Override
    @Deprecated
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printWeightedSetXml(this, xml);
    }

    /**
     * Returns the number of key-weight pairs in this set.
     *
     * @return the number of key-weight pairs in this set
     */
    public int size() {
        return map.size();
    }

    public boolean add(K value) {
        put(value, 1);
        return true;
    }

    @Override
    public Object getWrappedValue() {
        if (map instanceof WeightedSet.WeightedSetWrapper) {
            return ((WeightedSet.WeightedSetWrapper) map).map;
        }
        return map.getWrappedValue();
    }

    @Override
    public boolean contains(Object o) {
        return map.keySet().contains(o);
    }

    /**
     * Checks if this set is empty.
     *
     * @return true if the set is empty
     */
    public boolean isEmpty() {
        return map.isEmpty();
    }

    @Override
    public Iterator<K> iterator() {
        return map.keySet().iterator();
    }

    @Override
    public boolean removeValue(FieldValue o) {
        return super.removeValue(o, map.keySet());
    }

    /**
     * Checks whether this set contains the specified key.
     *
     * @param key the key to search for
     * @return true if this set contains this key
     */
    public boolean containsKey(Object key) {
        return map.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return map.containsValue(value);
    }

    /**
     * Returns the weight associated with the specified key.
     *
     * @param key the key to return the weight for
     * @return the weight associated with the specified key, or null (if not found)
     */
    public Integer get(Object key) {
        if (!(key instanceof FieldValue)) {
            throw new IllegalArgumentException("Only FieldValues are allowed as keys.");
        }
        IntegerFieldValue ifv = map.get(key);
        return ifv != null ? ifv.getInteger() : null;
    }

    /**
     * Add a key with an associated weight to this set. If the key is already present in this set, the previous
     * association is replaced. Checks to validate that all keys are of the same type.
     *
     * @param key    the key to add
     * @param weight the weight to associate with this key
     * @return the weight that was previously associated with this key, or null (if there was no previous key)
     */
    public Integer put(K key, Integer weight) {
        verifyElementCompatibility(key);
        IntegerFieldValue ifv =  putUnChecked(key, new IntegerFieldValue(weight));
        return ifv != null ? ifv.getInteger() : null;
    }

    /**
     * Add a key with an associated weight to this set. If the key is already present in this set, the previous
     * association is replaced.
     *
     * @param key    the key to add
     * @param weight the weight to associate with this key
     * @return the weight that was previously associated with this key, or null (if there was no previous key)
     */
    public IntegerFieldValue putUnChecked(K key, IntegerFieldValue weight) {
        return map.put(key, weight);
    }

    /**
     * Remove a key-weight association from this set.
     *
     * @param key the key to remove
     * @return the weight that was previously associated with this key, or null (if there was no previous key)
     */
    public Integer remove(Object key) {
        IntegerFieldValue ifv = map.remove(key);
        return ifv != null ? ifv.getInteger() : null;
    }

    public void putAll(Map<? extends K, ? extends Integer> t) {
        for (Entry<? extends K, ? extends Integer> entry : t.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /** Remove all key-weight associations in this set. */
    public void clear() {
        map.clear();
    }

    /**
     * Reserve space for this amount of keys in order to avoid resizing
     */
    public void clearAndReserve(int count) {
        map = new MapFieldValue<>(new MapDataType(getDataType().getNestedType(), DataType.INT), count);
    }

    Map<K, Integer> getPrimitiveMap() {
        Map<K, Integer> retVal = new LinkedHashMap<>();
        for (Entry<K, IntegerFieldValue> entry : map.entrySet()) {
            retVal.put(entry.getKey(), entry.getValue().getInteger());
        }
        return retVal;
    }

    public Collection<Integer> values() {
        return getPrimitiveMap().values();
    }

    public Set<K> keySet() {
        return map.keySet();
    }

    public Set<Entry<K, Integer>> entrySet() {
        return getPrimitiveMap().entrySet();
    }

    /**
     * Checks if another object is equal to this set.
     *
     * @param o the object to check for equality with
     * @return true if o is an instance of WeightedSet and the two encapsulated Maps are equal, false otherwise
     */
    public boolean equals(Object o) {
        if (!(o instanceof WeightedSet)) return false;
        WeightedSet w = (WeightedSet) o;
        if (size() != w.size()) return false;
        if ( ! super.equals(o)) return false;
        return map.equals(((WeightedSet)o).map);
    }

    /**
     * Uses hashCode() from the encapsulated Map.
     *
     * @return the hash code of this set
     */
    public int hashCode() {
        return map.hashCode();
    }

    /**
     * Uses toString() from the encapsulated Map.
     *
     * @return the toString() of this set
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("WeightedSet(").append(getDataType());
        for (Map.Entry entry : map.entrySet()) {
            sb.append("\n  key:   ").append(entry.getKey().getClass()).append(": ").append(entry.getKey());
            sb.append("\n  value: ").append(entry.getValue().getClass()).append(": ").append(entry.getValue());
        }
        return sb.append("\n)").toString();
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    @Override
    FieldPathIteratorHandler.ModificationStatus iterateNested(FieldPath fieldPath, int pos, FieldPathIteratorHandler handler) {
        FieldPathIteratorHandler.ModificationStatus status = map.iterateNested(fieldPath, pos, handler, this);
        return status;
    }

    @Override
    public int compareTo(FieldValue fieldValue) {
        int comp = super.compareTo(fieldValue);

        if (comp != 0) {
            return comp;
        }

        return map.compareTo(((WeightedSet<K>)fieldValue).map);
    }


    /**
     * Weighted set MapFieldValue, backed by map of native Java types.
     * Note: The key type of this is FieldValue, not K.
     * @author vegardh
     *
     */
    class WeightedSetWrapper extends MapFieldValue<K, IntegerFieldValue> {
        private final Map<Object, Integer> map;
        private final DataType keyTypeVespa;

        public WeightedSetWrapper(Map map, MapDataType dt) {
            super(dt);
            keyTypeVespa = getDataType().getKeyType();
            this.map=map;
        }

        private Object unwrap(Object o) {
            return (o instanceof FieldValue ? ((FieldValue) o).getWrappedValue() : o);
        }

        @SuppressWarnings("unchecked")
        private K wrapKey(Object o) {
            if (o==null) return null;
            return (K) keyTypeVespa.createFieldValue(o);
        }

        private IntegerFieldValue wrapValue(Object o) {
            if (o==null) return null;
            if (o instanceof IntegerFieldValue) return (IntegerFieldValue) o;
            if (o instanceof Integer) return new IntegerFieldValue((Integer) o);
            IntegerFieldValue value = new IntegerFieldValue();
            value.assign(o);
            return value;
        }

        @Override
        public void clear() {
            map.clear();
        }

        @Override
        public boolean containsKey(Object key) {
            return map.containsKey(unwrap(key));
        }

        @Override
        public boolean containsValue(Object value) {
            return map.containsValue(unwrap(value));
        }

        @Override
        public Set<java.util.Map.Entry<K, IntegerFieldValue>> entrySet() {
            Map<K, IntegerFieldValue> ret = new HashMap<>();
            for (Map.Entry e : map.entrySet()) {
                ret.put(wrapKey(e.getKey()), wrapValue(e.getValue()));
            }
            return ret.entrySet();
        }

        @Override
        public IntegerFieldValue get(Object key) {
            Object o = map.get(unwrap(key));
            return o == null ? null : wrapValue(o);
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public Set<K> keySet() {
            Set<K> ret = new HashSet<>();
            for (Map.Entry e : map.entrySet()) {
                ret.add(wrapKey(e.getKey()));
            }
            return ret;
        }

        @Override
        public IntegerFieldValue put(FieldValue key, IntegerFieldValue value) {
            IntegerFieldValue old = get(key);
            map.put(unwrap(key), (Integer) unwrap(value));
            return old;
        }

        @Override
        public void putAll(Map<? extends K, ? extends IntegerFieldValue> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                map.put(unwrap(e.getKey()), (Integer) unwrap(e.getValue()));
            }
        }

        @Override
        public IntegerFieldValue remove(Object key) {
            return wrapValue(map.remove(unwrap(key)));
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public Collection<IntegerFieldValue> values() {
            Collection<IntegerFieldValue> ret = new ArrayList<>();
            for (Object v : map.values()) {
                ret.add(wrapValue(v));
            }
            return ret;
        }

    }

}
