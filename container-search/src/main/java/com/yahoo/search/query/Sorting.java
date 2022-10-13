// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;
import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Specifies how a query is sorted by a list of fields with a sort order
 *
 * @author Arne Bergene Fossaa
 */
public class Sorting implements Cloneable {

    public static final String STRENGTH_IDENTICAL = "identical";
    public static final String STRENGTH_QUATERNARY = "quaternary";
    public static final String STRENGTH_TERTIARY = "tertiary";
    public static final String STRENGTH_SECONDARY = "secondary";
    public static final String STRENGTH_PRIMARY = "primary";
    public static final String UCA = "uca";
    public static final String RAW = "raw";
    public static final String LOWERCASE = "lowercase";

    private final List<FieldOrder> fieldOrders = new ArrayList<>(2);

    /** Creates an empty sort spec */
    public Sorting() { }

    public Sorting(List<FieldOrder> fieldOrders) {
        this.fieldOrders.addAll(fieldOrders);
    }

    /** Creates a sort spec from a string */
    public Sorting(String sortSpec) {
        setSpec(sortSpec, null);
    }

    /** Creates a sort spec from a string, for a given query. */
    public Sorting(String sortSpec, Query query) {
        IndexFacts.Session session = null;
        if (query != null && query.getModel().getExecution().context().getIndexFacts() != null)
            session = query.getModel().getExecution().context().getIndexFacts().newSession(query);
        setSpec(sortSpec, session);
    }

    /**
     * Creates a new sorting from the given string and returns it, or returns null if the argument does not contain
     * any sorting criteria (e.g it is null or the empty string)
     */
    public static Sorting fromString(String sortSpec) {
        if (sortSpec == null) return null;
        if ("".equals(sortSpec)) return null;
        return new Sorting(sortSpec);
    }

    private void setSpec(String rawSortSpec, IndexFacts.Session indexFacts) {
        for (String sortString : rawSortSpec.split(" ")) {
            // A sortspec element must be at least two characters long,
            // a sorting order and an attribute vector name
            if (sortString.length() < 1) continue;

            char orderMarker = sortString.charAt(0);
            int funcAttrStart = 0;
            if ((orderMarker == '+') || (orderMarker == '-')) {
                funcAttrStart = 1;
            }
            AttributeSorter sorter;
            int startPar = sortString.indexOf('(',funcAttrStart);
            int endPar = sortString.lastIndexOf(')');
            if ((startPar > 0) && (endPar > startPar)) {
                String functionName = sortString.substring(funcAttrStart, startPar);
                if (LOWERCASE.equalsIgnoreCase(functionName)) {
                    sorter = new LowerCaseSorter(canonic(sortString.substring(startPar+1, endPar), indexFacts));
                } else if (RAW.equalsIgnoreCase(functionName)) {
                    sorter = new RawSorter(canonic(sortString.substring(startPar+1, endPar), indexFacts));
                } else if (UCA.equalsIgnoreCase(functionName)) {
                    int commaPos = sortString.indexOf(',', startPar+1);
                    if ((startPar+1 < commaPos) && (commaPos < endPar)) {
                        int commaopt = sortString.indexOf(',', commaPos + 1);
                        UcaSorter.Strength strength = UcaSorter.Strength.UNDEFINED;
                        if (commaopt > 0) {
                            String s = sortString.substring(commaopt+1, endPar);
                            if (STRENGTH_PRIMARY.equalsIgnoreCase(s)) {
                                strength = UcaSorter.Strength.PRIMARY;
                            } else if (STRENGTH_SECONDARY.equalsIgnoreCase(s)) {
                                strength = UcaSorter.Strength.SECONDARY;
                            } else if (STRENGTH_TERTIARY.equalsIgnoreCase(s)) {
                                strength = UcaSorter.Strength.TERTIARY;
                            } else if (STRENGTH_QUATERNARY.equalsIgnoreCase(s)) {
                                strength = UcaSorter.Strength.QUATERNARY;
                            } else if (STRENGTH_IDENTICAL.equalsIgnoreCase(s)) {
                                strength = UcaSorter.Strength.IDENTICAL;
                            } else {
                                throw new IllegalInputException("Unknown collation strength: '" + s + "'");
                            }
                            sorter = new UcaSorter(canonic(sortString.substring(startPar+1, commaPos), indexFacts),
                                                   sortString.substring(commaPos+1, commaopt), strength);
                        } else {
                            sorter = new UcaSorter(canonic(sortString.substring(startPar+1, commaPos), indexFacts),
                                                   sortString.substring(commaPos+1, endPar), strength);
                        }
                    } else {
                        sorter = new UcaSorter(canonic(sortString.substring(startPar+1, endPar), indexFacts));
                    }
                } else {
                    if (functionName.isEmpty()) {
                        throw new IllegalInputException("No sort function specified");
                    } else {
                        throw new IllegalInputException("Unknown sort function '" + functionName + "'");
                    }
                }
            } else {
                sorter = new AttributeSorter(canonic(sortString.substring(funcAttrStart), indexFacts));
            }
            Order order = Order.UNDEFINED;
            if (funcAttrStart != 0) {
                // Override in sortspec
                order = (orderMarker == '+') ? Order.ASCENDING : Order.DESCENDING;
            }
            fieldOrders.add(new FieldOrder(sorter, order));
        }
    }

    private String canonic(String attributeName, IndexFacts.Session indexFacts) {
        if (indexFacts == null) return attributeName;
        return indexFacts.getCanonicName(attributeName);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        String space = "";
        for (FieldOrder spec : fieldOrders)   {
            sb.append(space);
            if (spec.getSortOrder() == Order.DESCENDING) {
                sb.append("-");
            } else {
                sb.append("+");
            }
            sb.append(spec.getFieldName());
            space = " ";
        }
        return sb.toString();
    }

    public enum Order { ASCENDING, DESCENDING, UNDEFINED}

    /**
     * Returns the field orders of this sort specification as list. This is never null but can be empty.
     * This list can be modified to change this sort spec.
     */
    public List<FieldOrder> fieldOrders() { return fieldOrders; }

    @Override
    public Sorting clone() {
        return new Sorting(this.fieldOrders);
    }

    @Override
    public int hashCode() {
        return fieldOrders.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if( ! (o instanceof Sorting ss)) return false;
        return fieldOrders.equals(ss.fieldOrders);
    }

    public int encode(ByteBuffer buffer) {
        int usedBytes = 0;
        byte[] nameBuffer;
        byte space = '.';
        for (FieldOrder fieldOrder : fieldOrders) {
            if (space == ' ')   {
                buffer.put(space);
                usedBytes++;
            }
            if (fieldOrder.getSortOrder() == Order.ASCENDING) {
                buffer.put((byte) '+');
            } else {
                buffer.put((byte) '-');
            }
            usedBytes++;
            nameBuffer = Utf8.toBytes(fieldOrder.getSorter().toSerialForm());
            buffer.put(nameBuffer);
            usedBytes += nameBuffer.length;
            // If this isn't the last element, append a separating space
            //if (i + 1 < sortSpec.size()) {
            space = ' ';
        }
        return usedBytes;
    }

    public static class AttributeSorter implements Cloneable {

        private static final Pattern legalAttributeName = Pattern.compile("[\\[]*[a-zA-Z_][\\.a-zA-Z0-9_-]*[\\]]*");

        private String fieldName;

        public AttributeSorter(String fieldName) {
            if ( ! legalAttributeName.matcher(fieldName).matches())
                throw new IllegalInputException("Illegal attribute name '" + fieldName + "' for sorting. Requires '" + legalAttributeName.pattern() + "'");
            this.fieldName = fieldName;
        }

        public String getName() { return fieldName; }
        public void setName(String fieldName) { this.fieldName = fieldName; }

        /** Returns the serial form of this which contains all information needed to reconstruct this sorter */
        public String toSerialForm() { return fieldName; }

        @Override
        public String toString() { return toSerialForm(); }

        @Override
        public int hashCode() { return fieldName.hashCode(); }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof AttributeSorter sorter)) {
                return false;
            }
            return sorter.fieldName.equals(fieldName);
        }

        @Override
        public AttributeSorter clone() {
            try {
                return (AttributeSorter)super.clone();
            }
            catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }

        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public int compare(Comparable a, Comparable b) {
            return a.compareTo(b);
        }

    }

    public static class RawSorter extends AttributeSorter {

        public RawSorter(String fieldName) { super(fieldName); }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof RawSorter)) {
                return false;
            }
            return super.equals(other);
        }
    }

    public static class LowerCaseSorter extends AttributeSorter {

        public LowerCaseSorter(String fieldName) { super(fieldName); }

        @Override
        public String toSerialForm() { return "lowercase(" + getName() + ')'; }

        @Override
        public int hashCode() { return 1 + 3*super.hashCode(); }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof LowerCaseSorter)) {
                return false;
            }
            return super.equals(other);
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        public int compare(Comparable a, Comparable b) {
            if ((a instanceof String) && (b instanceof String)) {
                return ((String)a).compareToIgnoreCase((String) b);
            }
            return a.compareTo(b);
        }
    }

    public static class UcaSorter extends AttributeSorter {

        public enum Strength { PRIMARY, SECONDARY, TERTIARY, QUATERNARY, IDENTICAL, UNDEFINED };
        private String locale = null;
        private Strength strength = Strength.UNDEFINED;
        private Collator collator;
        public UcaSorter(String fieldName, String locale, Strength strength) { super(fieldName); setLocale(locale, strength); }
        public UcaSorter(String fieldName) { super(fieldName); }

        static private int strength2Collator(Strength strength) {
            return switch (strength) {
                case PRIMARY -> Collator.PRIMARY;
                case SECONDARY -> Collator.SECONDARY;
                case TERTIARY -> Collator.TERTIARY;
                case QUATERNARY -> Collator.QUATERNARY;
                case IDENTICAL -> Collator.IDENTICAL;
                case UNDEFINED -> Collator.PRIMARY;
            };
        }

        public void setLocale(String locale, Strength strength) {
            this.locale = locale;
            this.strength = strength;
            ULocale uloc;
            try {
                uloc = new ULocale(locale);
            } catch (Throwable e) {
                throw new IllegalArgumentException("ULocale '" + locale + "' failed", e);
            }
            try {
                collator = Collator.getInstance(uloc);
                if (collator == null) {
                    throw new IllegalArgumentException("No collator available for locale '" + locale + "'");
                }
            } catch (Throwable e) {
                throw new RuntimeException("Collator.getInstance(ULocale(" + locale + ")) failed", e);
            }
            collator.setStrength(strength2Collator(strength));
            // collator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        }

        public String getLocale() { return locale; }
        public Strength getStrength() { return strength; }
        Collator getCollator() { return collator; }
        public String getDecomposition() { return (collator.getDecomposition() == Collator.CANONICAL_DECOMPOSITION) ? "CANONICAL_DECOMPOSITION" : "NO_DECOMPOSITION"; }

        @Override
        public String toSerialForm() {
            return "uca(" + getName() + ',' + locale + ',' +
                   ((strength != Strength.UNDEFINED) ? strength.toString() : "PRIMARY") + ')';
        }

        @Override
        public int hashCode() { return 1 + 3*locale.hashCode() + 5*strength.hashCode() + 7*super.hashCode(); }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof UcaSorter)) return false;
            return super.equals(other) && locale.equals(((UcaSorter)other).locale) && (strength == ((UcaSorter)other).strength);
        }

        @Override
        public UcaSorter clone() {
            UcaSorter clone = (UcaSorter)super.clone();
            if (locale != null) {
                clone.setLocale(locale, strength);
            }
            return clone;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        @Override
        public int compare(Comparable a, Comparable b) {
            if ((a instanceof String) && (b instanceof String)) {
                return collator.compare((String)a, (String) b);
            }
            return a.compareTo(b);
        }
    }

    /**
     * An attribute (field) and how it should be sorted
     */
    public static class FieldOrder implements Cloneable {

        private AttributeSorter fieldSorter;
        private Order sortOrder;

        /**
         * Creates an attribute vector
         *
         * @param fieldSorter the sorter of this attribute
         * @param sortOrder    whether to sort this ascending or descending
         */
        public FieldOrder(AttributeSorter fieldSorter, Order sortOrder) {
            this.fieldSorter = fieldSorter;
            this.sortOrder = sortOrder;
        }

        /**
         * Returns the name of this attribute
         */
        public String getFieldName() {
            return fieldSorter.getName();
        }

        /**
         * Returns the sorter of this attribute
         */
        public AttributeSorter getSorter() { return fieldSorter; }
        public void setSorter(AttributeSorter sorter) { fieldSorter = sorter; }

        /**
         * Returns the sorting order of this attribute
         */
        public Order getSortOrder() {
            return sortOrder;
        }

        /**
         * Decide if sortorder is ascending or not.
         */
        public void setAscending(boolean asc) {
            sortOrder = asc ? Order.ASCENDING : Order.DESCENDING;
        }

        @Override
        public String toString() {
            return sortOrder.toString() + ":" + fieldSorter.toString();
        }

        @Override
        public int hashCode() {
            return sortOrder.hashCode() + 17 * fieldSorter.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FieldOrder other)) return false;
            return other.sortOrder.equals(sortOrder) && other.fieldSorter.equals(fieldSorter);
        }

        @Override
        public FieldOrder clone() {
            return new FieldOrder(fieldSorter.clone(), sortOrder);
        }

    }

}
