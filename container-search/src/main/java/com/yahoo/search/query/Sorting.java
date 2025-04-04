// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query;

import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import com.yahoo.prelude.IndexFacts;
import com.yahoo.processing.IllegalInputException;
import com.yahoo.search.Query;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
    static final String MISSING = "missing";

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
        var tokenizer = new Tokenizer(rawSortSpec);
        while (tokenizer.skipSpaces()) {

            char orderMarker = tokenizer.peek();
            if (orderMarker == '+' || orderMarker == '-') {
                tokenizer.step();
            }
            var functionName = tokenizer.token();
            var inMissing = false;
            if (tokenizer.peek() == '(' && MISSING.equalsIgnoreCase(functionName)) {
                inMissing = true;
                tokenizer.step();
                functionName = tokenizer.token();
            }
            AttributeSorter sorter;
            if (tokenizer.peek() == '(') {
                tokenizer.step();
                if (LOWERCASE.equalsIgnoreCase(functionName)) {
                    sorter = new LowerCaseSorter(canonic(tokenizer.token(), indexFacts));
                    tokenizer.expectChar(')');
                } else if (RAW.equalsIgnoreCase(functionName)) {
                    sorter = new RawSorter(canonic(tokenizer.token(), indexFacts));
                    tokenizer.expectChar(')');
                } else if (UCA.equalsIgnoreCase(functionName)) {
                    var attrName = tokenizer.token();
                    if (tokenizer.expectChars(',', ')') == ',') {
                        UcaSorter.Strength strength = UcaSorter.Strength.UNDEFINED;
                        var locale = tokenizer.token();
                        if (tokenizer.expectChars(',', ')') == ',') {
                            var s = tokenizer.token();
                            tokenizer.expectChar(')');
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
                        }
                        sorter = new UcaSorter(canonic(attrName, indexFacts), locale, strength);
                    } else {
                        sorter = new UcaSorter(canonic(attrName, indexFacts));
                    }
                } else {
                    if (functionName.isEmpty()) {
                        throw new IllegalInputException("No sort function specified at " + tokenizer.spec());
                    } else {
                        throw new IllegalInputException("Unknown sort function '" + functionName + "' at " + tokenizer.spec());
                    }
                }
            } else {
                sorter = new AttributeSorter(canonic(functionName, indexFacts));
            }
            Order order = Order.UNDEFINED;
            if (orderMarker == '+' || orderMarker == '-') {
                // Override in sortspec
                order = (orderMarker == '+') ? Order.ASCENDING : Order.DESCENDING;
            }
            MissingPolicy missingPolicy = MissingPolicy.DEFAULT;
            String missingValue = null;
            if (inMissing) {
                tokenizer.expectChar(',');
                missingPolicy = decodeMissingPolicy(tokenizer);
                if (missingPolicy == MissingPolicy.AS) {
                    tokenizer.expectChar(',');
                    missingValue = decodeMissingValue(tokenizer);
                }
                tokenizer.expectChar(')');
            }
            fieldOrders.add(new FieldOrder(sorter, order, missingPolicy, missingValue));
            if (tokenizer.valid()) {
                tokenizer.expectChar(' ');
            }
        }
    }

    private static MissingPolicy decodeMissingPolicy(Tokenizer tokenizer) {
        var policyName = tokenizer.token();
        try {
            return MissingPolicy.valueOf(policyName.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalInputException("Unknown missing policy '" + policyName +"' at " + tokenizer.spec());
        }
    }

    private static String decodeMissingValue(Tokenizer tokenizer) {
        if (tokenizer.peek() == '"') {
            return tokenizer.dequoteString();
        } else {
            return tokenizer.token();
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

    enum MissingPolicy {
        DEFAULT("default"),
        FIRST("first"),
        LAST("last"),
        AS("as");
        private final String name;
        MissingPolicy(String name) { this.name = name; }
        String getName() { return name; }
    }

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

    public String toSerialForm() {
        var b = new StringBuilder();
        var first = true;
        for (FieldOrder fieldOrder : fieldOrders) {
            if (first) {
                first = false;
            } else {
                b.append(' ');
            }
            fieldOrder.appendSerialForm(b, true);
        }
        return b.toString();
    }

    public int encode(ByteBuffer buffer) {
        var serialForm = toSerialForm();
        var b = serialForm.getBytes(StandardCharsets.UTF_8);
        buffer.put(b);
        return b.length;
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

    private static class MissingValueSettings {
        private MissingPolicy missingPolicy;
        private String missingValue;

        MissingValueSettings(MissingPolicy missingPolicy, String missingValue) {
            this.missingPolicy = missingPolicy;
            this.missingValue = missingValue;
        }

        boolean hasNondefaultSetting() { return missingPolicy != MissingPolicy.DEFAULT; }

        void appendPrefix(StringBuilder b) {
            b.append("missing(");
        }

        static boolean needQuotes(String value) {
            if (value.isEmpty()) return true;
            for (int i = 0; i < value.length(); ++i) {
                var c = value.charAt(i);
                if (c == ' ' || c == ',' || c == '(' || c == ')' || c == '\\' || c == '"') {
                    return true;
                }
            }
            return false;
        }

        static void appendQuoted(StringBuilder b, String value) {
            b.append('"');
            for (int i = 0; i < value.length(); ++i) {
                var c = value.charAt(i);
                if (c == '\\' || c == '"') {
                    b.append('\\');
                }
                b.append(c);
            }
            b.append('"');
        }

        void appendMissingValue(StringBuilder b)
        {
            if (missingValue == null) {
                b.append("\"\"");
            } else {
                if (needQuotes(missingValue)) {
                    appendQuoted(b, missingValue);
                } else {
                    b.append(missingValue);
                }
            }
        }

        void appendSuffix(StringBuilder b) {
            b.append(',');
            b.append(missingPolicy.getName());
            if (missingPolicy == MissingPolicy.AS) {
                b.append(",");
                appendMissingValue(b);
            }
            b.append(')');
        }

        @Override
        public int hashCode() {
            return Objects.hash(missingPolicy, missingValue);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof MissingValueSettings other)) return false;
            if (missingPolicy != other.missingPolicy) return false;
            if (missingValue == null) {
                return other.missingValue == null;
            }
            if (other.missingValue == null) return false;
            return missingValue.equals(other.missingValue);
        }

        MissingPolicy getPolicy() {
            return missingPolicy;
        }

        String getMissingValue() {
            return missingValue;
        }
    }

    /**
     * An attribute (field) and how it should be sorted
     */
    public static class FieldOrder implements Cloneable {

        private AttributeSorter fieldSorter;
        private Order sortOrder;
        private MissingValueSettings missingValueSettings;

        /**
         * Creates an attribute vector
         *
         * @param fieldSorter the sorter of this attribute
         * @param sortOrder    whether to sort this ascending or descending
         */
        public FieldOrder(AttributeSorter fieldSorter, Order sortOrder) {
            this(fieldSorter, sortOrder, MissingPolicy.DEFAULT, null);
        }

        /**
         * Creates an attribute vector
         *
         * @param fieldSorter the sorter of this attribute
         * @param sortOrder    whether to sort this ascending or descending
         * @param missingPolicy policy for handling of missing value
         * @param missingValue replacement value
         */
        FieldOrder(AttributeSorter fieldSorter, Order sortOrder, MissingPolicy missingPolicy, String missingValue) {
            this.fieldSorter = fieldSorter;
            this.sortOrder = sortOrder;
            this.missingValueSettings = new MissingValueSettings(missingPolicy, missingValue);
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

        MissingValueSettings getMissingValueSettings() {
            return missingValueSettings;
        }

        void appendSerialForm(StringBuilder b, boolean includeOrder) {
            if (includeOrder) {
                if (sortOrder == Order.ASCENDING) {
                    b.append('+');
                } else {
                    b.append('-');
                }
            }
            boolean emitMissingFunction = missingValueSettings.hasNondefaultSetting();
            if (emitMissingFunction) {
                missingValueSettings.appendPrefix(b);
            }
            b.append(fieldSorter.toSerialForm());
            if (emitMissingFunction) {
                missingValueSettings.appendSuffix(b);
            }
        }

        public String toSerialForm(boolean includeOrder) {
            var b = new StringBuilder();
            appendSerialForm(b, includeOrder);
            return b.toString();
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
            return Objects.hash(sortOrder, fieldSorter, missingValueSettings);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FieldOrder other)) return false;
            return other.sortOrder.equals(sortOrder) && other.fieldSorter.equals(fieldSorter) &&
                    other.missingValueSettings.equals(missingValueSettings);
        }

        @Override
        public FieldOrder clone() {
            return new FieldOrder(fieldSorter.clone(), sortOrder, missingValueSettings.getPolicy(),
                    missingValueSettings.getMissingValue());
        }

    }

    private static class Tokenizer {
        private String spec;
        private int pos;
        public Tokenizer(String spec) {
            this.spec = spec;
            pos = 0;
        }

        String token() {
            if (pos >= spec.length()) {
                return new String();
            }
            var oldPos = pos;
            while (pos < spec.length()) {
                var c = spec.charAt(pos);
                if (c == ' ' || c == ',' || c == '(' || c == ')' || c == '\\' || c == '"') {
                    break;
                }
                ++pos;
            }
            return spec.substring(oldPos, pos);
        }

        boolean valid() {
            return pos < spec.length();
        }

        char peek() {
            return (pos < spec.length()) ? spec.charAt(pos) : '\0';
        }

        void step() {
            if (valid()) {
                ++pos;
            }
        }

        boolean skipSpaces() {
            while(valid() && spec.charAt(pos) == ' ') {
                ++pos;
            }
            return valid();
        }

        String spec() {
            var builder = new StringBuilder();
            builder.append('[');
            builder.append(spec.substring(0, pos));
            builder.append(']');
            builder.append('[');
            builder.append(spec.substring(pos));
            builder.append(']');
            return builder.toString();
        }

        void expectChar(char expected) {
            if (!valid()) {
                throw new IllegalInputException("Expected '" + expected + "', end of spec reached at " + spec());
            }
            var act = peek();
            if (act != expected) {
                throw new IllegalInputException("Expected '" + expected + "', got '" + act + "' at " + spec());
            }
            step();
        }

        char expectChars(char expected1, char expected2) {
            if (!valid()) {
                throw new IllegalInputException("Expected '" + expected1 + "' or '" + expected2 +"', end of spec reached at " + spec());
            }
            var act = peek();
            if (act != expected1 && act != expected2) {
                throw new IllegalInputException("Expected '" + expected1 + "' or '" + expected2 + "', got '" + act + "' at " + spec());
            }
            step();
            return act;
        }

        String dequoteString() {
            var b = new StringBuilder();
            expectChar('"');
            while (valid() && peek() != '"') {
                var fragment = token();
                b.append(fragment);
                if (valid()) {
                    var c = peek();
                    if (c == '\\') {
                        step();
                        c = expectChars('\\', '"');
                        b.append(c);
                    } else if (c != '"') {
                        b.append(c);
                        step();
                    }
                }
            }
            expectChar('"');
            return b.toString();
        }
    }

}
