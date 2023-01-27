// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.FieldPath;
import com.yahoo.document.MapDataType;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlSerializationHelper;
import com.yahoo.document.serialization.XmlStream;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;


/**
 * Vespa map. Backed by and and parametrized by FieldValue
 *
 * @author vegardh
 */
public class MapFieldValue<K extends FieldValue, V extends FieldValue> extends CompositeFieldValue implements java.util.Map<K,V> {

    private java.util.Map<K,V> values;

    public MapFieldValue(MapDataType type) {
        this(type, 1);
    }

    public MapFieldValue(MapDataType type, int initialCapacity) {
        super(type);
        values = new HashMap<K, V>(initialCapacity);
    }

    @Override
    public MapDataType getDataType() {
        return (MapDataType)super.getDataType();
    }

    @Override
    public void assign(Object o) {
        if (!checkAssign(o)) {
            return;
        }

        if (o instanceof MapFieldValue) {
            if (o == this) return;
            MapFieldValue a = (MapFieldValue) o;
            values.clear();
            putAll(a);
        } else if (o instanceof Map) {
            values = new MapWrapper((Map)o);
        }
        else {
            throw new IllegalArgumentException("Class " + o.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    @Override
    public MapFieldValue clone() {
        MapFieldValue copy = (MapFieldValue) super.clone();
        copy.values = new HashMap<K, V>(values.size());
        for (Map.Entry<K, V> entry : values.entrySet()) {
            copy.values.put(entry.getKey().clone(), entry.getValue().clone());
        }
        return copy;
    }

    /**
     * Checks if another object is equal to this set.
     *
     * @param o the object to check for equality with
     * @return true if o is an instance of WeightedSet and the two encapsulated Maps are equal, false otherwise
     */
    public boolean equals(Object o) {
        if (!(o instanceof MapFieldValue)) return false;
        MapFieldValue m = (MapFieldValue) o;
        if (size() != m.size()) return false;
        if ( ! super.equals(m)) return false;
        return entrySet().equals(m.entrySet());
    }

    @Override
    public void clear() {
        values.clear();
    }

    @Override
    public void deserialize(Field field, FieldReader reader) {
        reader.read(field, this);
    }

    @Override
    @Deprecated
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printMapXml(this, xml);
    }

    @Override
    public void serialize(Field field, FieldWriter writer) {
        writer.write(field, this);
    }

    @Override
    public Object getWrappedValue() {
        if (values instanceof MapFieldValue.MapWrapper) {
            return ((MapFieldValue.MapWrapper) values).map;
        }
        Map tmpMap = new HashMap();
        for (Entry<K, V> kvEntry : values.entrySet()) {
            tmpMap.put(kvEntry.getKey().getWrappedValue(), kvEntry.getValue().getWrappedValue());
        }
        return tmpMap;
    }

    ///// java.util.Map methods

    public boolean containsKey(Object key) {
        return values.containsKey(key);
    }

    public boolean containsValue(Object value) {
        return values.containsValue(value);
    }

    public Set<java.util.Map.Entry<K, V>> entrySet() {
        return values.entrySet();
    }

    public V get(Object key) {
        return values.get(key);
    }

    public Set<K> keySet() {
        return values.keySet();
    }

    private void validateCompatibleTypes(DataType d, FieldValue v) {
        if (!d.isValueCompatible(v)) {
            throw new IllegalArgumentException(
                    "Incompatible data types. Got " + v.getDataType()
                    + ", expected " + d);
        }
    }

    public V put(K key, V value) {
        validateCompatibleTypes(getDataType().getKeyType(), key);
        validateCompatibleTypes(getDataType().getValueType(), value);
        return values.put(key, value);
    }

    public void putAll(java.util.Map<? extends K, ? extends V> m) {
        for (K key : m.keySet()) {
            validateCompatibleTypes(getDataType().getKeyType(), key);
        }
        for (V value : m.values()) {
            validateCompatibleTypes(getDataType().getValueType(), value);
        }
        values.putAll(m);
    }

    public V remove(Object key) {
        return values.remove(key);
    }

    public Collection<V> values() {
        return values.values();
    }

    public boolean contains(Object o) {
        return values.containsKey(o);
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    boolean checkAndRemove(FieldValue key, FieldPathIteratorHandler.ModificationStatus status, boolean wasModified, List<FieldValue> keysToRemove) {
        if (status == FieldPathIteratorHandler.ModificationStatus.REMOVED) {
            keysToRemove.add(key);
            return true;
        } else if (status == FieldPathIteratorHandler.ModificationStatus.MODIFIED) {
            return true;
        }

        return wasModified;
    }

   FieldPathIteratorHandler.ModificationStatus iterateNested(FieldPath fieldPath, int pos, FieldPathIteratorHandler handler, FieldValue complexFieldValue) {
        List<FieldValue> keysToRemove = new ArrayList<FieldValue>();
        boolean wasModified = false;

        if (pos < fieldPath.size()) {
            switch (fieldPath.get(pos).getType()) {
                case MAP_KEY:
                {
                    FieldValue val = values.get(fieldPath.get(pos).getLookupKey());
                    if (val != null) {
                        wasModified = checkAndRemove(fieldPath.get(pos).getLookupKey(), val.iterateNested(fieldPath, pos + 1, handler), wasModified, keysToRemove);
                    } else if (handler.createMissingPath()) {
                        val = getDataType().getValueType().createFieldValue();
                        FieldPathIteratorHandler.ModificationStatus status = val.iterateNested(fieldPath, pos + 1, handler);
                        if (status == FieldPathIteratorHandler.ModificationStatus.MODIFIED) {
                            put((K)fieldPath.get(pos).getLookupKey(), (V)val);
                            return status;
                        }
                    }
                    break;
                }
                case MAP_ALL_KEYS:
                    for (FieldValue f : values.keySet()) {
                        wasModified = checkAndRemove(f, f.iterateNested(fieldPath, pos + 1, handler), wasModified, keysToRemove);
                    }
                    break;
                case MAP_ALL_VALUES:
                    for (Map.Entry<K, V> entry : values.entrySet()) {
                        wasModified = checkAndRemove(entry.getKey(), entry.getValue().iterateNested(fieldPath, pos + 1, handler), wasModified, keysToRemove);
                    }
                    break;
                case VARIABLE:
                {
                    FieldPathIteratorHandler.IndexValue idx = handler.getVariables().get(fieldPath.get(pos).getVariableName());
                    if (idx != null) {
                        FieldValue val = values.get(idx.getKey());
                        if (val != null) {
                            wasModified = checkAndRemove(idx.getKey(), val.iterateNested(fieldPath, pos + 1, handler), wasModified, keysToRemove);
                        }
                    } else {
                        for (Map.Entry<K, V> entry : values.entrySet()) {
                            handler.getVariables().put(fieldPath.get(pos).getVariableName(), new FieldPathIteratorHandler.IndexValue(entry.getKey()));
                            wasModified = checkAndRemove(entry.getKey(), entry.getValue().iterateNested(fieldPath, pos + 1, handler), wasModified, keysToRemove);
                        }
                        handler.getVariables().remove(fieldPath.get(pos).getVariableName());
                    }
                    break;
                }
                default:
                    for (Map.Entry<K, V> entry : values.entrySet()) {
                        wasModified = checkAndRemove(entry.getKey(), entry.getKey().iterateNested(fieldPath, pos, handler), wasModified, keysToRemove);
                    }
                    break;
            }
        } else {
            FieldPathIteratorHandler.ModificationStatus status = handler.modify(complexFieldValue);
            if (status == FieldPathIteratorHandler.ModificationStatus.REMOVED) {
                return status;
            } else if (status == FieldPathIteratorHandler.ModificationStatus.MODIFIED) {
                wasModified = true;
            }

            if (handler.onComplex(complexFieldValue)) {
                for (Map.Entry<K, V> entry : values.entrySet()) {
                    wasModified = checkAndRemove(entry.getKey(), entry.getKey().iterateNested(fieldPath, pos, handler), wasModified, keysToRemove);
                }
            }
        }

        for (FieldValue f : keysToRemove) {
            values.remove(f);
        }

        return wasModified ? FieldPathIteratorHandler.ModificationStatus.MODIFIED : FieldPathIteratorHandler.ModificationStatus.NOT_MODIFIED;
    }

    @Override
    FieldPathIteratorHandler.ModificationStatus iterateNested(FieldPath fieldPath, int pos, FieldPathIteratorHandler handler) {
        return iterateNested(fieldPath, pos, handler, this);
    }

    @Override
    public int compareTo(FieldValue fieldValue) {
        int comp = super.compareTo(fieldValue);

        if (comp != 0) {
            return comp;
        }
        //types are equal, this must be of this type
        MapFieldValue<K,V> rhs = (MapFieldValue<K,V>) fieldValue;
        if (size() < rhs.size()) {
            return -1;
        } else if (size() > rhs.size()) {
            return 1;
        }
        Map.Entry<K,V> [] entries = entrySet().toArray(new Map.Entry[size()]);
        Map.Entry<K,V> [] rhsEntries = rhs.entrySet().toArray(new Map.Entry[rhs.size()]);
        Arrays.sort(entries, Comparator.comparing(Map.Entry<K,V>::getKey));
        Arrays.sort(rhsEntries, Comparator.comparing(Map.Entry<K,V>::getKey));
        for (int i = 0; i < entries.length; i++) {
            comp = entries[i].getKey().compareTo(rhsEntries[i].getKey());
            if (comp != 0) return comp;
            comp = entries[i].getValue().compareTo(rhsEntries[i].getValue());
            if (comp != 0) return comp;
        }

        return 0;
    }

    /**
     * Map of field values backed by a normal map of Java objects
     * @author vegardh
     *
     */
    class MapWrapper implements Map<K,V> {

        private Map<Object,Object> map; // Not field values, basic objects
        private DataType keyTypeVespa = getDataType().getKeyType();
        private DataType valTypeVespa = getDataType().getValueType();
        public MapWrapper(Map map) {
            this.map=map;
        }

        private Object unwrap(Object o) {
            return (o instanceof FieldValue ? ((FieldValue) o).getWrappedValue() : o);
        }

        private K wrapKey(Object o) {
            if (o==null) return null;
            return (K)keyTypeVespa.createFieldValue(o);
        }

        private V wrapValue(Object o) {
            if (o==null) return null;
            return (V)valTypeVespa.createFieldValue(o);
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
        public Set<java.util.Map.Entry<K, V>> entrySet() {
            Map<K, V> ret = new HashMap<K, V>();
            for (Map.Entry e : map.entrySet()) {
                ret.put(wrapKey(e.getKey()), wrapValue(e.getValue()));
            }
            return ret.entrySet();
        }

        @Override
        public V get(Object key) {
            Object o = map.get(unwrap(key));
            return o == null ? null : wrapValue(o);
        }

        @Override
        public boolean isEmpty() {
            return map.isEmpty();
        }

        @Override
        public Set<K> keySet() {
            Set<K> ret = new HashSet<K>();
            for (Map.Entry e : map.entrySet()) {
                ret.add(wrapKey(e.getKey()));
            }
            return ret;
        }

        @Override
        public V put(K key, V value) {
            V old = get(key);
            map.put(unwrap(key), unwrap(value));
            return old;
        }

        @Override
        public void putAll(Map<? extends K, ? extends V> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                map.put(unwrap(e.getKey()), unwrap(e.getValue()));
            }
        }

        @Override
        public V remove(Object key) {
            return wrapValue(map.remove(unwrap(key)));
        }

        @Override
        public int size() {
            return map.size();
        }

        @Override
        public Collection<V> values() {
            Collection<V> ret = new ArrayList<V>();
            for (Object v : map.values()) {
                ret.add(wrapValue(v));
            }
            return ret;
        }

    }

}
