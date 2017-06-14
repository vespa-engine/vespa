// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.datatypes;

import com.google.common.collect.ImmutableList;
import com.yahoo.collections.CollectionComparator;
import com.yahoo.document.DataType;
import com.yahoo.document.Field;
import com.yahoo.document.PrimitiveDataType;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.serialization.FieldReader;
import com.yahoo.document.serialization.FieldWriter;
import com.yahoo.document.serialization.XmlSerializationHelper;
import com.yahoo.document.serialization.XmlStream;
import com.yahoo.vespa.objects.Ids;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A StringFieldValue is a wrapper class that holds a String in {@link com.yahoo.document.Document}s and
 * other {@link com.yahoo.document.datatypes.FieldValue}s.
 *
 * @author Einar M R Rosenvinge
 */
public class StringFieldValue extends FieldValue {

    private static class Factory extends PrimitiveDataType.Factory {
        public FieldValue create() {
            return new StringFieldValue();
        }
    }
    public static PrimitiveDataType.Factory getFactory() { return new Factory(); }
    public static final int classId = registerClass(Ids.document + 15, StringFieldValue.class);
    private String value;
    private Map<String, SpanTree> spanTrees = null;
    private static final boolean[] allowedAsciiChars = new boolean[0x80];

    static {
        allowedAsciiChars[0x0] = false;
        allowedAsciiChars[0x1] = false;
        allowedAsciiChars[0x2] = false;
        allowedAsciiChars[0x3] = false;
        allowedAsciiChars[0x4] = false;
        allowedAsciiChars[0x5] = false;
        allowedAsciiChars[0x6] = false;
        allowedAsciiChars[0x7] = false;
        allowedAsciiChars[0x8] = false;
        allowedAsciiChars[0x9] = true;  //tab
        allowedAsciiChars[0xA] = true;  //nl
        allowedAsciiChars[0xB] = false;
        allowedAsciiChars[0xC] = false;
        allowedAsciiChars[0xD] = true;  //cr
        for (int i = 0xE; i < 0x20; i++) {
            allowedAsciiChars[i] = false;
        }
        for (int i = 0x20; i < 0x7F; i++) {
            allowedAsciiChars[i] = true;  //printable ascii chars
        }
        allowedAsciiChars[0x7F] = true;  //del - discouraged, but allowed
    }


    /** Creates a new StringFieldValue holding an empty String. */
    public StringFieldValue() {
        value = "";
    }

    /**
     * Creates a new StringFieldValue with the given value.
     *
     * @param value the value to wrap.
     */
    public StringFieldValue(String value) {
        if (value==null) throw new IllegalArgumentException("Value cannot be null");
        setValue(value);
    }

    private void setValue(String value) {
        for (int i = 0; i < value.length(); i++) {
            char theChar = value.charAt(i);
            int codePoint = value.codePointAt(i);
            if (Character.isHighSurrogate(theChar)) {
                //skip one char ahead, since codePointAt() consumes one more char in this case
                ++i;
            }

            //See http://www.w3.org/TR/2006/REC-xml11-20060816/#charsets

            if (codePoint < 0x80) {  //ascii
                if (allowedAsciiChars[codePoint]) {
                    continue;
                } else {
                    throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
                }
            }

            //source cited above notes that 0x7F-0x84 and 0x86-0x9F are discouraged, but they are still allowed.
            //see http://www.w3.org/International/questions/qa-controls

            if (codePoint < 0xFDD0) {
                continue;
            }
            if (codePoint <= 0xFDDF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }

            if (codePoint < 0x1FFFE) {
                continue;
            }
            if (codePoint <= 0x1FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0x2FFFE) {
                continue;
            }
            if (codePoint <= 0x2FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0x3FFFE) {
                continue;
            }
            if (codePoint <= 0x3FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0x4FFFE) {
                continue;
            }
            if (codePoint <= 0x4FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0x5FFFE) {
                continue;
            }
            if (codePoint <= 0x5FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0x6FFFE) {
                continue;
            }
            if (codePoint <= 0x6FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0x7FFFE) {
                continue;
            }
            if (codePoint <= 0x7FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0x8FFFE) {
                continue;
            }
            if (codePoint <= 0x8FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0x9FFFE) {
                continue;
            }
            if (codePoint <= 0x9FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0xAFFFE) {
                continue;
            }
            if (codePoint <= 0xAFFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0xBFFFE) {
                continue;
            }
            if (codePoint <= 0xBFFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0xCFFFE) {
                continue;
            }
            if (codePoint <= 0xCFFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0xDFFFE) {
                continue;
            }
            if (codePoint <= 0xDFFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0xEFFFE) {
                continue;
            }
            if (codePoint <= 0xEFFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0xFFFFE) {
                continue;
            }
            if (codePoint <= 0xFFFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
            if (codePoint < 0x10FFFE) {
                continue;
            }
            if (codePoint <= 0x10FFFF) {
                throw new IllegalArgumentException("StringFieldValue cannot contain code point 0x" + Integer.toHexString(codePoint).toUpperCase());
            }
        }
        this.value = value;
    }

    /**
     * Returns {@link com.yahoo.document.DataType}.STRING.
     *
     * @return DataType.STRING, always
     * @see com.yahoo.document.DataType
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
            strfval.spanTrees = new HashMap<String, SpanTree>(spanTrees.size());
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
            return ImmutableList.of();
        }
        return ImmutableList.copyOf(spanTrees.values());
    }

    /**
     *
     * @return The map of spantrees. Might be null.
     */
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
            spanTrees = new HashMap(1);
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
     * @return the span tree previously associated with the given name, or null if it did not exist.
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

    /**
     * Returns the String value wrapped by this StringFieldValue.
     *
     * @return the String value wrapped by this StringFieldValue.
     */
    public String getString() {
        return value;
    }

    /**
     * Returns the String value wrapped by this StringFieldValue.
     *
     * @return the String value wrapped by this StringFieldValue.
     */
    @Override
    public Object getWrappedValue() {
        return value;
    }

    /**
     * Prints XML in Vespa Document XML format for this StringFieldValue.
     *
     * @param xml the stream to print to.
     */
    @Override
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
        if (!(o instanceof StringFieldValue)) return false;
        if (!super.equals(o)) return false;
        StringFieldValue that = (StringFieldValue) o;
        if ((spanTrees != null) ? !spanTrees.equals(that.spanTrees) : that.spanTrees != null) return false;
        if ((value != null) ? !value.equals(that.value) : that.value != null) return false;
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
