// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

/**
 * A named rank table of a certain type.
 *
 * @author geirst
 */
public class NativeTable {

    private String name;

    private Type type;

    /** A table type enumeration */
    public static class Type {

        public static Type FIRST_OCCURRENCE = new Type("firstOccurrenceTable");
        public static Type OCCURRENCE_COUNT = new Type("occurrenceCountTable");
        public static Type WEIGHT = new Type("weightTable");
        public static Type PROXIMITY = new Type("proximityTable");
        public static Type REVERSE_PROXIMITY = new Type("reverseProximityTable");

        private String name;

        private Type(String name) {
            this.name = name;
        }

        public String getName() { return name; }

        public boolean equals(Object object) {
            if (!(object instanceof Type)) {
                return false;
            }
            Type other = (Type)object;
            return this.name.equals(other.name);
        }

        public int hashCode() {
            return name.hashCode();
        }

        public String toString() {
            return getName();
        }
    }

    public NativeTable(Type type, String name) {
        this.type = type;
        this.name = name;
    }

    public Type getType() { return type; }

    public String getName() { return name; }

    public int hashCode() {
        return type.hashCode() + 17*name.hashCode();
    }

    public boolean equals(Object object) {
        if (! (object instanceof NativeTable)) return false;
        NativeTable other = (NativeTable)object;
        return other.getName().equals(this.getName()) && other.getType().equals(this.getType());
    }

    public String toString() {
        return getType() + ": " + getName();
    }

}
