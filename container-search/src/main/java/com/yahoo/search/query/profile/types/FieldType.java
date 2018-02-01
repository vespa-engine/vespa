// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.profile.types;

import com.yahoo.search.query.profile.QueryProfile;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.yql.YqlQuery;
import com.yahoo.tensor.Tensor;

/**
 * Superclass of query type field types.
 * Field types are immutable.
 *
 * @author bratseth
 */
@SuppressWarnings("rawtypes")
public abstract class FieldType {

    public static final PrimitiveFieldType stringType = new PrimitiveFieldType(String.class);
    public static final PrimitiveFieldType integerType = new PrimitiveFieldType(Integer.class);
    public static final PrimitiveFieldType longType = new PrimitiveFieldType(Long.class);
    public static final PrimitiveFieldType floatType = new PrimitiveFieldType(Float.class);
    public static final PrimitiveFieldType doubleType = new PrimitiveFieldType(Double.class);
    public static final PrimitiveFieldType booleanType = new PrimitiveFieldType(Boolean.class);
    public static final QueryFieldType queryType = new QueryFieldType();
    public static final QueryProfileFieldType genericQueryProfileType = new QueryProfileFieldType();

    /** Returns the class of instance values of this field type */
    public abstract Class getValueClass();

    /** Returns a string representation of this type which can be converted back to a type class by {@link #fromString} */
    public abstract String stringValue();

    public abstract String toString();

    /** Returns a string describing possible instances of this type, suitable for user error messages */
    public abstract String toInstanceDescription();

    /** Converts the given type to an instance of this type, if possible. Returns null if not possible. */
    public abstract Object convertFrom(Object o, QueryProfileRegistry registry);

    /** Converts the given type to an instance of this type, if possible. Returns null if not possible. */
    public abstract Object convertFrom(Object o, CompiledQueryProfileRegistry registry);

    /**
     * Returns the field type for a given string name.
     *
     * @param  typeString a type string - a primitive name, "query-profile" or "query-profile:profile-name"
     * @param  registry the registry in which query profile references are resolved when the last form above is used,
     *         or null in which case that form cannot be used
     * @throws IllegalArgumentException if the string does not resolve to a type
     */
    public static FieldType fromString(String typeString, QueryProfileTypeRegistry registry) {
        if ("string".equals(typeString))
            return stringType;
        if ("integer".equals(typeString))
            return integerType;
        if ("long".equals(typeString))
            return longType;
        if ("float".equals(typeString))
            return floatType;
        if ("double".equals(typeString))
            return doubleType;
        if ("boolean".equals(typeString))
            return booleanType;
        if ("query".equals(typeString))
            return queryType;
        if (typeString.startsWith("tensor"))
            return TensorFieldType.fromTypeString(typeString);
        if ("query-profile".equals(typeString))
            return genericQueryProfileType;
        if (typeString.startsWith("query-profile:"))
            return QueryProfileFieldType.fromString(typeString.substring("query-profile:".length()),registry);
        throw new IllegalArgumentException("Unknown type '" + typeString + "'");
    }

    /** Returns true if the given object is a legal field value of some field value type */
    public static boolean isLegalFieldValue(Object value) {
        Class clazz = value.getClass();
        if (clazz == String.class) return true;
        if (clazz == Integer.class) return true;
        if (clazz == Long.class) return true;
        if (clazz == Float.class) return true;
        if (clazz == Double.class) return true;
        if (clazz == Boolean.class) return true;
        if (clazz == YqlQuery.class) return true;
        if (clazz == QueryProfile.class) return true;
        if (clazz == Tensor.class) return true;
        return false;
    }

}
