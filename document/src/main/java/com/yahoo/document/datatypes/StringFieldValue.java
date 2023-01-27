// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.yahoo.collections.CollectionComparator;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.annotation.SpanTree;
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

    private static class Factory extends PrimitiveDataType.Factory {
        @Override public FieldValue create() { return new StringFieldValue(); }
        @Override public FieldValue create(String value) { return new StringFieldValue(value); }
    }

    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 15, StringFieldValue.class);
    private String value;
    private Map<String, SpanTree> spanTrees = null;

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

    private static void validateTextString(String value) {
        if ( ! Text.isValidTextString(value)) {
            throw new IllegalArgumentException("The string field value contains illegal code point 0x" +
                                               Integer.toHexString(Text.validateTextString(value).getAsInt()).toUpperCase());
        }
    }

    private void setValue(String value) {
        validateTextString(value);
        this.value = value;
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
     * Clones this StringFieldValue and its span trees.
     *
     * @return a new deep-copied StringFieldValue
     */
    @Override
    public StringFieldValue clone() {
        StringFieldValue strfval = (StringFieldValue) super.clone();
        if (spanTrees != null) {
            strfval.spanTrees = new HashMap<>(spanTrees.size());
            for (Map.Entry<String, SpanTree> entry : spanTrees.entrySet()) {
                strfval.spanTrees.put(entry.getKey(), new SpanTree(entry.getValue()));
            }
        }
        return strfval;
    }

    /** Sets the wrapped String to be an empty String, and clears all span trees. */
    @Override
    public void clear() {
        value = "";
        if (spanTrees != null) {
            spanTrees.clear();
            spanTrees = null;
        }
    }

    /**
     * Sets a new value for this StringFieldValue.&nbsp;NOTE that doing so will clear all span trees from this value,
     * since they most certainly will not make sense for a new string value.
     *
     * @param o the new String to assign to this. An argument of null is equal to calling clear().
     * @throws IllegalArgumentException if the given argument is a string containing non-text characters as defined by 
     *                                  {@link Text#isTextCharacter(int)}
     */
    @Override
    public void assign(Object o) {
        if (spanTrees != null) {
            spanTrees.clear();
            spanTrees = null;
        }

        if (!checkAssign(o)) {
            return;
        }
        if (o instanceof StringFieldValue) {
            spanTrees=((StringFieldValue)o).spanTrees;
        }
        if (o instanceof String) {
            setValue((String) o);
        } else if (o instanceof StringFieldValue || o instanceof NumericFieldValue) {
            setValue(o.toString());
        } else {
            throw new IllegalArgumentException("Class " + o.getClass() + " not applicable to an " + this.getClass() + " instance.");
        }
    }

    /**
     * Returns an unmodifiable Collection of the span trees with annotations over this String, if any.
     *
     * @return an unmodifiable Collection of the span trees with annotations over this String, or an empty Collection
     */
    public Collection<SpanTree> getSpanTrees() {
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
    public SpanTree getSpanTree(String name) {
        if (spanTrees == null) {
            return null;
        }
        return spanTrees.get(name);
    }

    /**
     * Sets the span tree with annotations over this String.
     *
     * @param spanTree the span tree with annotations over this String
     * @return the input spanTree for chaining
     * @throws IllegalArgumentException if a span tree with the given name already exists.
     */
    public SpanTree setSpanTree(SpanTree spanTree) {
        if (spanTrees == null) {
            spanTrees = new HashMap<>(1);
        }
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
        if (spanTrees == null) {
            return null;
        }
        SpanTree tree = spanTrees.remove(name);
        if (tree != null) {
            tree.setStringFieldValue(null);
        }
        return tree;
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
        if (!Objects.equals(spanTrees, that.spanTrees)) return false;
        if (!Objects.equals(value, that.value)) return false;
        return true;
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

        if (spanTrees == null) {
            comp = (otherValue.spanTrees == null) ? 0 : -1;
        } else {
            if (otherValue.spanTrees == null) {
                comp = 1;
            } else {
                comp = CollectionComparator.compare(spanTrees.keySet(), otherValue.spanTrees.keySet());
                if (comp != 0) {
                    return comp;
                }
                comp = CollectionComparator.compare(spanTrees.values(), otherValue.spanTrees.values());
            }
        }
        return comp;
    }

    /**
     * Only for use by deserializer to avoid the cost of verifying input.
     */
    public void setUnChecked(String s) {
        value = s;
    }

}
