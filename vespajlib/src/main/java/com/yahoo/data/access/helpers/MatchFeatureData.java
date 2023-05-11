// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access.helpers;

import com.yahoo.collections.Hashlet;

import com.yahoo.data.access.ArrayTraverser;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.ObjectTraverser;
import com.yahoo.data.access.Type;
import com.yahoo.data.access.simple.Value;

import java.util.ArrayList;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * MatchFeatureData helps pack match features for hits into
 * inspectable HitValue objects, all sharing the same Hashlet
 * for the field names.
 * @author arnej
 */
public class MatchFeatureData {

    private final Hashlet<String,Integer> hashlet;

    public MatchFeatureData(List<String> keys) {
        this.hashlet = new Hashlet<>();
        hashlet.reserve(keys.size());
        int i = 0;
        for (String key : keys) {
            hashlet.put(key, i++);
        }
    }

    public static class HitValue extends Value {
        private final Hashlet<String,Integer> hashlet;
        private final byte[][] dataValues;
        private final double[] doubleValues;

        public Type type() { return Type.OBJECT; }
        public boolean valid() { return true; }
        public int fieldCount() { return hashlet.size(); }
        public void traverse(ObjectTraverser ot) {
            for (int i = 0; i < hashlet.size(); i++) {
                String fn = hashlet.key(i);
                int offset = hashlet.value(i);
                ot.field(fn, valueAt(offset));
            }
        }
        public Inspector field(String name) {
            int offset = hashlet.getIndexOfKey(name);
            if (offset < 0) {
                return invalid();
            }
            return valueAt(offset);
        }
        public Iterable<Map.Entry<String,Inspector>> fields() {
            var list = new ArrayList<Map.Entry<String,Inspector>>(hashlet.size());
            for (int i = 0; i < hashlet.size(); i++) {
                String fn = hashlet.key(i);
                int offset = hashlet.value(i);
                list.add(new SimpleEntry<String,Inspector>(fn, valueAt(offset)));
            }
            return list;
        }

        // use from enclosing class only
        private HitValue(Hashlet<String,Integer> hashlet) {
            this.hashlet = hashlet;
            this.dataValues = new byte[hashlet.size()][];
            this.doubleValues = new double[hashlet.size()];
        }

        public void set(int index, byte[] data) {
            dataValues[index] = data;
        }
        public void set(int index, double value) {
            doubleValues[index] = value;
        }

        private Inspector valueAt(int index) {
            if (dataValues[index] != null) {
                return new Value.DataValue(dataValues[index]);
            }
            return new Value.DoubleValue(doubleValues[index]);
        }

        public HitValue subsetFilter(Function<Hashlet<String,Integer>, Hashlet<String,Integer>> filter) {
            return new HitValue(filter.apply(hashlet), dataValues, doubleValues);
        }
        // used only from subsetFilter() above
        private HitValue(Hashlet<String,Integer> hashlet, byte[][] dataValues, double[] doubleValues) {
            this.hashlet = hashlet;
            this.dataValues = dataValues;
            this.doubleValues = doubleValues;
        }
    }

    public HitValue addHit() {
        return new HitValue(hashlet);
    }
    
}
