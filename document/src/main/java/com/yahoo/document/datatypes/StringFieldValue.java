// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.collections.CollectionComparator;
import com.yahoo.data.disclosure.DataSink;
import com.yahoo.data.disclosure.DataSource;
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
import java.util.Locale;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * A StringFieldValue is a wrapper class that holds a String in {@link com.yahoo.document.Document}s and
 * other {@link com.yahoo.document.datatypes.FieldValue}s.
 *
 * String fields can only contain text characters, as defined by {@link Text#isTextCharacter(int)}
 *
 * @author Einar M R Rosenvinge
 */
@SuppressWarnings({"deprecation", "removal"})
public class StringFieldValue extends FieldValue implements DataSource {

    private static final Logger log = Logger.getLogger(StringFieldValue.class.getName());

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
                                                    Integer.toHexString(Text.validateTextString(value).getAsInt()).toUpperCase(Locale.ROOT));
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
        boolean hasSimple = simpleAnnotations != null;
        boolean hasFull = spanTrees != null;

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

    private void clearAnnotations() {
        // Clear all annotations
        simpleAnnotations = null;
        if (spanTrees != null) {
            spanTrees.clear();
            spanTrees = null;
        }
    }

    /**
     * Ensure any "simple" annotations are converted to full SpanTree.
     */
    private void convertAnySimpleAnnotations() {
        // Convert SIMPLE→FULL if needed
        if (simpleAnnotations != null) {
            if (shouldLogSimpleToFull()) {
                log.warning("Converting from SIMPLE to FULL annotation mode - this may indicate inefficient code path");
            }
            spanTrees = new HashMap<>(1);
            var tree = simpleAnnotations.toSpanTree(SpanTrees.LINGUISTICS);
            tree.setStringFieldValue(this);
            spanTrees.put(SpanTrees.LINGUISTICS, tree);
            simpleAnnotations = null;
        }
    }

    private static boolean shouldLogSimpleToFull() {
        int count = simpleToFullCounter.getAndAdd(1);
        return shouldLogForCount(count);
    }
    private static final AtomicInteger simpleToFullCounter = new AtomicInteger();
    private static boolean shouldLogForCount(int count) {
        if (count < 100) return true;
        if (count < 1000) return (count % 100) == 0;
        if (count < 100000) return (count % 1000) == 0;
        return (count % 10000) == 0;
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
                copy.spanTrees = new HashMap<>(spanTrees.size());
                for (Map.Entry<String, SpanTree> entry : spanTrees.entrySet()) {
                    var tree = new SpanTree(entry.getValue());
                    tree.setStringFieldValue(copy);
                    copy.spanTrees.put(entry.getKey(), tree);
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
        clearAnnotations();
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
        clearAnnotations();
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
                    if (spanTrees != null) {
                        // Steal span trees
                        for (var tree : spanTrees.values()) {
                            tree.setStringFieldValue(this);
                        }
                    }
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
        convertAnySimpleAnnotations();
        if (spanTrees == null) {
            return List.of();
        }
        return List.copyOf(spanTrees.values());
    }

    /** Returns the map of spantrees. Might be null. */
    public final Map<String, SpanTree> getSpanTreeMap() {
        convertAnySimpleAnnotations();
        return spanTrees;
    }

    /**
     * Returns the span tree associated with the given name, or null if this does not exist.
     *
     * @param name the name of the span tree to return
     * @return the span tree associated with the given name, or null if this does not exist.
     */
    public SpanTree getSpanTree(String name) {
        convertAnySimpleAnnotations();
        return spanTrees != null ? spanTrees.get(name) : null;
    }

    /**
     * Checks whether a span tree with the given name exists.
     * This is more efficient than getSpanTree(name) != null because it doesn't
     * force conversion from simple to full annotation mode.
     *
     * @param name the name of the span tree to check for
     * @return true if a span tree with this name exists (in either simple or full mode)
     */
    public boolean hasAnnotations(String name) {
        // Check simple mode for LINGUISTICS tree
        if (simpleAnnotations != null && SpanTrees.LINGUISTICS.equals(name)) {
            return true;
        }
        // Check full mode
        return spanTrees != null && spanTrees.containsKey(name);
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
        convertAnySimpleAnnotations();
        if (spanTrees == null)
            spanTrees = new HashMap<>(1);
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
     * Creates an empty SimpleIndexingAnnotations suiteable for this field.
     * Must be passed to setSimpleAnnotations later to be useful.
     * Public for use by indexing expressions, but not part of stable API.
     *
     * @return SimpleIndexingAnnotations instance, or null if simple mode not enabled or already using full SpanTree
     */
    public boolean wantSimpleAnnotations() {
        if (!SimpleIndexingAnnotations.isEnabled()) {
            return false;
        }
        if (getAnnotationMode() == AnnotationMode.FULL) {
            return false;
        }
        return true;
    }

    /**
     * Returns the simple annotations if present
     * Public for use by serialization, but not part of stable API.
     */
    public SimpleIndexingAnnotations getSimpleAnnotations() {
        return simpleAnnotations;
    }

    /**
     * Sets the simple annotations for this field.
     * Public for use by deserializer, but not part of stable API.
     */
    public void setSimpleAnnotations(SimpleIndexingAnnotations simple) {
        // Clear existing annotations, then set simple
        clearAnnotations();
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

    @Override
    public void emit(DataSink sink) {
        sink.stringValue(value);
    }

}
