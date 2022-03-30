// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.SubstituteString;

import static com.yahoo.text.Lowercase.toLowerCase;

/**
 * Represents a query field type which is a primitive - String, Integer, Float, Double or Long.
 *
 * @author bratseth
 */
@SuppressWarnings("rawtypes")
public class PrimitiveFieldType extends FieldType {

    private final Class primitiveClass;

    PrimitiveFieldType(Class primitiveClass) {
        this.primitiveClass = primitiveClass;
    }

    @Override
    public Class getValueClass() { return primitiveClass; }

    @Override
    public String stringValue() {
        return toLowerCase(primitiveClass.getSimpleName());
    }

    @Override
    public String toString() { return "field type " + stringValue(); }

    @Override
    public String toInstanceDescription() {
        return toLowerCase(primitiveClass.getSimpleName());
    }

    @Override
    public Object convertFrom(Object object, ConversionContext context) {
        return convertFrom(object, (QueryProfileRegistry)null);
    }

    @Override
    public Object convertFrom(Object object, QueryProfileRegistry registry) {
        if (primitiveClass == object.getClass()) return object;
        if (primitiveClass == String.class && object.getClass() == SubstituteString.class) return object;

        if (object.getClass() == String.class) return convertFromString((String)object);
        if (object instanceof Number) return convertFromNumber((Number)object);
        return null;
    }

    private Object convertFromString(String string) {
        try {
            if (primitiveClass == Integer.class) return Integer.valueOf(string);
            if (primitiveClass == Double.class) return Double.valueOf(string);
            if (primitiveClass == Float.class) return Float.valueOf(string);
            if (primitiveClass == Long.class) return Long.valueOf(string);
            if (primitiveClass == Boolean.class) return Boolean.valueOf(string);
        }
        catch (NumberFormatException e) {
            return null; // Handled in caller
        }
        throw new RuntimeException("Programming error");
    }

    private Object convertFromNumber(Number number) {
        if (primitiveClass == Integer.class) return number.intValue();
        if (primitiveClass == Double.class) return number.doubleValue();
        if (primitiveClass == Float.class) return number.floatValue();
        if (primitiveClass == Long.class) return number.longValue();
        if (primitiveClass == String.class) return String.valueOf(number);
        throw new RuntimeException("Programming error: Input type is " + number.getClass() +
                                   " primitiveClass is " + primitiveClass);
    }

    @Override
    public int hashCode() {
        return primitiveClass.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof PrimitiveFieldType)) return false;
        PrimitiveFieldType other = (PrimitiveFieldType)o;
        return other.primitiveClass.equals(this.primitiveClass);
    }

}
