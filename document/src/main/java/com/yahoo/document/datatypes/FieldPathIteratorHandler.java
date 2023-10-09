// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import java.util.Map;
import java.util.TreeMap;

/**
 * @author Thomas Gundersen
 */
public abstract class FieldPathIteratorHandler {

    public static class IndexValue {

        private int index;
        private FieldValue key;

        public int getIndex() {
            return index;
        }

        public FieldValue getKey() {
            return key;
        }

        public IndexValue() {
            index = -1;
            key = null;
        }

        public IndexValue(int index) {
            this.index = index;
            key = null;
        }

        public IndexValue(FieldValue key) {
            index = -1;
            this.key = key;
        }

        public String toString() {
            if (key != null) {
                return key.toString();
            } else {
                return "" + index;
            }
        }

        @Override
        public int hashCode() {
            int hc = index;
            if (key != null) {
                hc = key.hashCode();
            }
            return hc;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof IndexValue)) {
                return false;
            }
            IndexValue other = (IndexValue)o;
            if (key != null && other.key != null) {
                return key.equals(other.key);
            }
            if (key == null && other.key == null) {
                return index == other.index;
            }
            return false;
        }
    };

    public static class VariableMap extends TreeMap<String, IndexValue> {

        @Override
        public Object clone() {
            Map<String, IndexValue> map = new VariableMap();
            map.putAll(this);
            return map;
        }
    }

    private VariableMap variables = new VariableMap();

    public void onPrimitive(FieldValue fv) {

    }

    public boolean onComplex(FieldValue fv) {
        return true;
    }

    public ModificationStatus doModify(FieldValue fv) {
        return ModificationStatus.NOT_MODIFIED;
    }

    public enum ModificationStatus {
        MODIFIED, REMOVED, NOT_MODIFIED
    }

    public ModificationStatus modify(FieldValue fv) {
        return doModify(fv);
    }

    public boolean createMissingPath() {
        return false;
    }

    public VariableMap getVariables() {
        return variables;
    }

}
