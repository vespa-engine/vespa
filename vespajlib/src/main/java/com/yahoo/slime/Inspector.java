// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

import java.util.function.Consumer;

/**
 * Interface for read-only access to any value or object that is part
 * of a Slime. You can access meta-data such as validity and actual
 * type.  You can always convert to any basic type by calling the
 * various "as" accessor methods; these return a default value if the
 * current Inspector is invalid or the type doesn't match your
 * accessor type.  If you want to do something exceptional instead
 * when the types don't match, you must check using type() first.
 * 
 * @author havardpe
 */
public interface Inspector {

    /** check if this inspector is valid */
    boolean valid();

    /** Invoke the given consumer with this value if it is valid */
    void ifValid(Consumer<Inspector> consumer);

    /** return an enum describing value type */
    Type type();

    /**
     * Check how many entries or fields are contained in the current value.
     * Useful for arrays and objects; anything else always returns 0.
     * @return number of entries/fields contained.
     */
    int children();

    /**
     * Check how many entries are contained in the current value.
     * Useful for arrays; anything else always returns 0.
     * @return number of entries contained.
     */
    int entries();

    /**
     * Check how many fields are contained in the current value.
     * Useful for objects; anything else always returns 0.
     * @return number of fields contained.
     */
    int fields();

    /** the current value (for booleans); default: false */
    boolean asBool();

    /** the current value (for integers); default: 0 */
    long asLong();

    /** the current value (for floating-point values); default: 0.0 */
    double asDouble();

    /** the current value (for string values); default: empty string */
    String asString();

    /** the current value encoded into UTF-8 (for string values); default: empty array */
    byte[] asUtf8();

    /** the current value (for data values); default: empty array */
    byte[] asData();

    /**
     * Use the visitor pattern to resolve the underlying type of this value.
     * @param v the visitor
     */
    void accept(Visitor v);

    /**
     * Traverse an array value, performing callbacks for each entry.
     *
     * If the current Inspector is connected to an array value,
     * perform callbacks to the given traverser for each entry
     * contained in the array.
     * @param at traverser callback object.
     */
    @SuppressWarnings("overloads")
    void traverse(ArrayTraverser at);

    /**
     * Traverse an object value, performing callbacks for each field.
     *
     * If the current Inspector is connected to an object value,
     * perform callbacks to the given traverser for each field
     * contained in the object.
     * @param ot traverser callback object.
     */
    @SuppressWarnings("overloads")
    void traverse(ObjectSymbolTraverser ot);

    /**
     * Traverse an object value, performing callbacks for each field.
     *
     * If the current Inspector is connected to an object value,
     * perform callbacks to the given traverser for each field
     * contained in the object.
     * @param ot traverser callback object.
     */
    @SuppressWarnings("overloads")
    void traverse(ObjectTraverser ot);

    /**
     * Access an array entry.
     *
     * If the current Inspector doesn't connect to an array value,
     * or the given array index is out of bounds, the returned
     * Inspector will be invalid.
     * @param idx array index.
     * @return a new Inspector for the entry value.
     */
    Inspector entry(int idx);

    /**
     * Access an field in an object by symbol id.
     *
     * If the current Inspector doesn't connect to an object value, or
     * the object value does not contain a field with the given symbol
     * id, the returned Inspector will be invalid.
     * @param sym symbol id.
     * @return a new Inspector for the field value.
     */
    Inspector field(int sym);

    /**
     * Access an field in an object by symbol name.
     *
     * If the current Inspector doesn't connect to an object value, or
     * the object value does not contain a field with the given symbol
     * name, the returned Inspector will be invalid.
     * @param name symbol name.
     * @return a new Inspector for the field value.
     */
    Inspector field(String name);

    /**
     * Tests whether this is equal to Inspector.
     *
     * Since equality of two Inspectors is subtle, {@link Object#equals(Object)} is not used.
     *
     * @param that inspector.
     * @return true if they are equal.
     */
    boolean equalTo(Inspector that);
}
