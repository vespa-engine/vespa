// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.collections.CollectionComparator;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.annotation.internal.SimpleIndexingAnnotations;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlSerializationHelper;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.text.Text;
import com.yahoo.vespa.objects.Ids;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A StringFieldValue is a wrapper class that holds a String in {@link com.yahoo.document.Document}s and
 * other {@link com.yahoo.document.datatypes.FieldValue}s.
 *
 * String fields can only contain text characters, as defined by {@link Text#isTextCharacter(int)}
 *
 * @author Einar M R Rosenvinge
 */
public class StringFieldValue extends FieldValue {

    // TODO: remove this, it's a temporary workaround for invalid data stored before unicode validation was fixed
    private static final boolean replaceInvalidUnicode = System.getProperty("vespa.replace_invalid_unicode", "false").equals("true");


    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new StringFieldValue(); }
        @Override public FieldValue create(String value) { return new StringFieldValue(value); }
    }

    /** Annotation storage modes - at most one can be active at a time */
    private enum AnnotationMode {
        NONE,           // No annotations
        SIMPLE,         // Using simpleAnnotations (lightweight)
        FULL            // Using spanTrees (full SpanTree objects)
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 15, StringFieldValue.class);
    private String value;
    private Map<String, SpanTree> spanTrees = null;
    private SimpleIndexingAnnotations simpleAnnotations = null;  // Used when USE_SIMPLE_ANNOTATIONS is true

    /** Creates a new StringFieldValue holding an empty String. */
    public StringFieldValue() {
        value = "";
    }

    /**
     * Creates a new StringFieldValue with the given value.
     *
     * @param value the value to wrap.
     * @throws IllegalArgumentException if the string contains non-text characters as defined by
     *                                  {@link Text#isTextCharacter(int)}
     */
    public StringFieldValue(String value) {
        if (value == null) throw new IllegalArgumentException("Value cannot be null");
        setValue(value);
    }

    private static String validateTextString(String value) {
        if ( ! Text.isValidTextString(value)) {
            if (replaceInvalidUnicode) return Text.stripInvalidCharacters(value);
            else throw new IllegalArgumentException("The string field value contains illegal code point 0x" +
                                                    Integer.toHexString(Text.validateTextString(value).getAsInt()).toUpperCase());
        }
        return value;
    }

    private void setValue(String value) {
        this.value = validateTextString(value);
    }

    /**
     * Returns the current annotation mode.
     * Validates that at most one annotation storage mechanism is active.
     */
    private AnnotationMode getAnnotationMode() {
        boolean hasSimple = simpleAnnotations != null && simpleAnnotations.getCount() > 0;
        boolean hasFull = spanTrees != null && !spanTrees.isEmpty();

        if (hasSimple && hasFull) {
            throw new IllegalStateException(
                "Invariant violation: Both simple and full annotations exist! " +
                "simpleAnnotations.count=" + simpleAnnotations.getCount() + ", " +
                "spanTrees.size=" + spanTrees.size());
        }

        if (hasSimple) return AnnotationMode.SIMPLE;
        if (hasFull) return AnnotationMode.FULL;
        return AnnotationMode.NONE;
    }

    /**
     * Validates the invariant that at most one annotation storage is active.
     * Throws IllegalStateException if both exist.
     */
    private void assertInvariant() {
        // This will throw if invariant is violated
        getAnnotationMode();
    }

    /**
     * Changes the annotation mode, handling conversions atomically.
     * This is the single point where mode transitions occur.
     *
     * @param targetMode the desired annotation mode
     */
    private void changeAnnotationMode(AnnotationMode targetMode) {
        AnnotationMode currentMode = getAnnotationMode();
        if (currentMode == targetMode) {
            return;  // Already in target mode
        }

        switch (targetMode) {
            case NONE:
                // Clear all annotations
                simpleAnnotations = null;
                if (spanTrees != null) {
                    spanTrees.clear();
                    spanTrees = null;
                }
                break;

            case SIMPLE:
                // Should not convert FULL→SIMPLE (not supported)
                if (currentMode == AnnotationMode.FULL) {
                    throw new IllegalStateException(
                        "Cannot convert from FULL to SIMPLE annotation mode");
                }
                // From NONE→SIMPLE is handled by createSimpleAnnotations()
                break;

            case FULL:
                // Convert SIMPLE→FULL if needed
                if (currentMode == AnnotationMode.SIMPLE) {
                    spanTrees = new HashMap<>(1);
                    var tree = simpleAnnotations.toSpanTree(SpanTrees.LINGUISTICS);
                    tree.setStringFieldValue(this);
                    spanTrees.put(SpanTrees.LINGUISTICS, tree);
                    simpleAnnotations = null;
                } else if (currentMode == AnnotationMode.NONE) {
                    // From NONE→FULL, just ensure spanTrees exists
                    if (spanTrees == null) {
                        spanTrees = new HashMap<>(1);
                    }
                }
                break;
        }

        assertInvariant();
    }

    /**
     * Returns {@link com.yahoo.document.DataType}.STRING.
     *
     * @return DataType.STRING, always
     */
    @Override
    public DataType getDataType() {
        return DataType.STRING;
    }

    /**
     * Clones this StringFieldValue and its annotations (both simple and full span trees).
     *
     * @return a new deep-copied StringFieldValue
     */
    @Override
    public StringFieldValue clone() {
        StringFieldValue copy = (StringFieldValue) super.clone();

        // Deep copy based on current annotation mode
        switch (getAnnotationMode()) {
            case SIMPLE:
                // Deep copy simple annotations
                copy.simpleAnnotations = new SimpleIndexingAnnotations();
                for (int i = 0; i < simpleAnnotations.getCount(); i++) {
                    copy.simpleAnnotations.add(
                        simpleAnnotations.getFrom(i),
                        simpleAnnotations.getLength(i),
                        simpleAnnotations.getTerm(i)
                    );
                }
                copy.spanTrees = null;
                break;

            case FULL:
                // Deep copy span trees
                copy.spanTrees = new HashMap<>(spanTrees.size());
                for (Map.Entry<String, SpanTree> entry : spanTrees.entrySet()) {
                    copy.spanTrees.put(entry.getKey(), new SpanTree(entry.getValue()));
                }
                copy.simpleAnnotations = null;
                break;

            case NONE:
                copy.spanTrees = null;
                copy.simpleAnnotations = null;
                break;
        }

        return copy;
    }

    /** Sets the wrapped String to be an empty String, and clears all span trees. */
    @Override
    public void clear() {
        value = "";
        changeAnnotationMode(AnnotationMode.NONE);
    }

    /**
     * Sets a new value for this StringFieldValue. NOTE that doing so will clear all span trees from this value,
     * since they most certainly will not make sense for a new string value.
     *
     * @param o the new String to assign to this. An argument of null is equal to calling clear().
     * @throws IllegalArgumentException if the given argument is a string containing non-text characters as defined by
     *                                  {@link Text#isTextCharacter(int)}
     */
    @Override
    public void assign(Object o) {
        // Clear existing annotations first
        changeAnnotationMode(AnnotationMode.NONE);

        if (!checkAssign(o)) {
            return;
        }

        if (o instanceof StringFieldValue other) {
            // Copy only one annotation type based on source's mode
            switch (other.getAnnotationMode()) {
                case SIMPLE:
                    simpleAnnotations = other.simpleAnnotations;
                    break;
                case FULL:
                    spanTrees = other.spanTrees;
                    break;
                case NONE:
                    // Already cleared
                    break;
            }
        }

        if (o instanceof String) {
            setValue((String) o);
        } else if (o instanceof StringFieldValue || o instanceof NumericFieldValue) {
            setValue(o.toString());
        } else {
            throw new IllegalArgumentException("Class " + o.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }

        assertInvariant();
    }

    /**
     * Returns an unmodifiable Collection of the span trees with annotations over this String, if any.
     *
     * @return an unmodifiable Collection of the span trees with annotations over this String, or an empty Collection
     */
    public Collection<SpanTree> getSpanTrees() {
        if (simpleAnnotations != null) {
            // Lazy conversion for API compatibility (rare path)
            return List.of(getSpanTree(SpanTrees.LINGUISTICS));
        }
        if (spanTrees == null) {
            return List.of();
        }
        return List.copyOf(spanTrees.values());
    }

    /** Returns the map of spantrees. Might be null. */
    public final Map<String, SpanTree> getSpanTreeMap() {
        return spanTrees;
    }

    /**
     * Returns the span tree associated with the given name, or null if this does not exist.
     *
     * @param name the name of the span tree to return
     * @return the span tree associated with the given name, or null if this does not exist.
     */
    private SpanTree simpleAsTree() {
        if ((simpleAnnotations == null) || (simpleAnnotations.getCount() == 0)) {
            return null;
        }
        var converted = simpleAnnotations.toSpanTree(SpanTrees.LINGUISTICS);
        converted.setStringFieldValue(this);
        return converted;
    }

    public SpanTree getSpanTree(String name) {
        // If in simple mode and requesting LINGUISTICS, promote to full mode (cache conversion)
        if (getAnnotationMode() == AnnotationMode.SIMPLE && SpanTrees.LINGUISTICS.equals(name)) {
            // Promote to full mode (one-time conversion for caching)
            changeAnnotationMode(AnnotationMode.FULL);
        }

        return spanTrees != null ? spanTrees.get(name) : null;
    }

    /**
     * Sets the span tree with annotations over this String.
     * Atomically converts from simple to full mode if needed.
     *
     * @param spanTree the span tree with annotations over this String
     * @return the input spanTree for chaining
     * @throws IllegalArgumentException if a span tree with the given name already exists.
     */
    public SpanTree setSpanTree(SpanTree spanTree) {
        // Ensure we're in full mode (converts if needed)
        changeAnnotationMode(AnnotationMode.FULL);

        if (spanTrees.containsKey(spanTree.getName())) {
            throw new IllegalArgumentException("Span tree " + spanTree.getName() + " already exists.");
        }

        spanTrees.put(spanTree.getName(), spanTree);
        spanTree.setStringFieldValue(this);

        return spanTree;
    }

    /**
     * Removes the span tree associated with the given name.
     *
     * @param name the name of the span tree to remove
     * @return the span tree previously associated with the given name, or null if it did not exist
     */
    public SpanTree removeSpanTree(String name) {
        if (simpleAnnotations != null && SpanTrees.LINGUISTICS.equals(name)) {
            SpanTree tree = simpleAnnotations.toSpanTree(name);
            simpleAnnotations = null;
            return tree;
        }
        if (spanTrees == null) {
            return null;
        }
        SpanTree tree = spanTrees.remove(name);
        if (tree != null) {
            tree.setStringFieldValue(null);
        }
        return tree;
    }

    /**
     * Creates or returns the SimpleIndexingAnnotations for this field.
     * Public for use by indexing expressions, but not part of stable API.
     *
     * @return SimpleIndexingAnnotations instance, or null if simple mode not enabled or already using full SpanTree
     */
    public SimpleIndexingAnnotations createSimpleAnnotations() {
        if (!SimpleIndexingAnnotations.isEnabled()) {
            return null;
        }
        if (spanTrees != null && !spanTrees.isEmpty()) {
            // Already using full mode
            return null;
        }
        spanTrees = null;
        if (simpleAnnotations == null) {
            simpleAnnotations = new SimpleIndexingAnnotations();
        }
        assertInvariant();
        return simpleAnnotations;
    }

    /**
     * Returns the simple annotations if present and non-empty
     * Public for use by serialization, but not part of stable API.
     */
    public SimpleIndexingAnnotations getSimpleAnnotations() {
        if (simpleAnnotations != null && simpleAnnotations.getCount() != 0) {
            return simpleAnnotations;
        } else {
            return null;
        }
    }

    /**
     * Sets the simple annotations for this field.
     * Public for use by deserializer, but not part of stable API.
     */
    public void setSimpleAnnotations(SimpleIndexingAnnotations simple) {
        // Clear existing annotations, then set simple
        changeAnnotationMode(AnnotationMode.NONE);
        this.simpleAnnotations = simple;
        assertInvariant();
    }

    /** Returns the String value wrapped by this StringFieldValue */
    public String getString() {
        return value;
    }

    /** Returns the String value wrapped by this StringFieldValue */
    @Override
    public Object getWrappedValue() {
        return value;
    }

    /**
     * Prints XML in Vespa Document XML format for this StringFieldValue.
     *
     * @param xml the stream to print to
     */
    @Override
    @Deprecated
    public void printXml(XmlStream xml) {
        XmlSerializationHelper.printStringXml(this, xml);
        //TODO: add spanTree printing
    }

    /**
     * Returns the String value wrapped by this StringFieldValue.
     *
     * @return the String value wrapped by this StringFieldValue.
     */
    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StringFieldValue that)) return false;
        if (!super.equals(o)) return false;
        if (!Objects.equals(value, that.value)) return false;

        // Compare annotations based on mode
        AnnotationMode thisMode = getAnnotationMode();
        AnnotationMode thatMode = that.getAnnotationMode();

        if (thisMode == AnnotationMode.NONE && thatMode == AnnotationMode.NONE) {
            return true;
        }

        if (thisMode == AnnotationMode.SIMPLE && thatMode == AnnotationMode.SIMPLE) {
            return Objects.equals(simpleAnnotations, that.simpleAnnotations);
        }

        if (thisMode == AnnotationMode.FULL && thatMode == AnnotationMode.FULL) {
            return Objects.equals(spanTrees, that.spanTrees);
        }

        // Different modes - would need semantic comparison via conversion
        // For now, consider them not equal if different modes
        return false;
    }

    @Override
    public int hashCode() {
        return (value != null) ? value.hashCode() : super.hashCode();
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
    public int compareTo(FieldValue fieldValue) {
        int comp = super.compareTo(fieldValue);

        if (comp != 0) {
            return comp;
        }

        //types are equal, this must be of this type
        StringFieldValue otherValue = (StringFieldValue) fieldValue;
        comp = value.compareTo(otherValue.value);

        if (comp != 0) {
            return comp;
        }

        // Compare annotations based on mode
        AnnotationMode thisMode = getAnnotationMode();
        AnnotationMode thatMode = otherValue.getAnnotationMode();

        // Compare modes first (NONE < SIMPLE < FULL for ordering)
        comp = thisMode.compareTo(thatMode);
        if (comp != 0) {
            return comp;
        }

        // Same mode - compare contents
        switch (thisMode) {
            case NONE:
                return 0;
            case SIMPLE:
                return compareSimpleAnnotations(otherValue);
            case FULL:
                if (spanTrees == null) {
                    return (otherValue.spanTrees == null) ? 0 : -1;
                } else {
                    if (otherValue.spanTrees == null) {
                        return 1;
                    }
                    comp = CollectionComparator.compare(spanTrees.keySet(), otherValue.spanTrees.keySet());
                    if (comp != 0) {
                        return comp;
                    }
                    return CollectionComparator.compare(spanTrees.values(), otherValue.spanTrees.values());
                }
        }

        return 0;
    }

    private int compareSimpleAnnotations(StringFieldValue other) {
        if (simpleAnnotations == null) {
            return (other.simpleAnnotations == null) ? 0 : -1;
        }
        if (other.simpleAnnotations == null) {
            return 1;
        }

        int comp = Integer.compare(simpleAnnotations.getCount(), other.simpleAnnotations.getCount());
        if (comp != 0) return comp;

        for (int i = 0; i < simpleAnnotations.getCount(); i++) {
            comp = Integer.compare(simpleAnnotations.getFrom(i), other.simpleAnnotations.getFrom(i));
            if (comp != 0) return comp;

            comp = Integer.compare(simpleAnnotations.getLength(i), other.simpleAnnotations.getLength(i));
            if (comp != 0) return comp;

            String thisTerm = simpleAnnotations.getTerm(i);
            String otherTerm = other.simpleAnnotations.getTerm(i);
            if (thisTerm == null) {
                comp = (otherTerm == null) ? 0 : -1;
            } else {
                comp = (otherTerm == null) ? 1 : thisTerm.compareTo(otherTerm);
            }
            if (comp != 0) return comp;
        }

        return 0;
    }

    /**
     * Only for use by deserializer to avoid the cost of verifying input.
     */
    public void setUnChecked(String s) {
        value = s;
    }

}
