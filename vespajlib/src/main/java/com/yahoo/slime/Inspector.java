// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Interface for read-only access to any value or object that is part
 * of a Slime. You can access meta-data such as validity and actual
 * type.  You can always convert to any basic type by calling the
 * various "as" accessor methods; these return a default value if the
 * current Inspector is invalid or the type doesn't match your
 * accessor type.  If you want to do something exceptional instead
 * when the types don't match, you must check using type() first.
 **/
public interface Inspector {

    /** check if this inspector is valid */
    public boolean valid();

    /** return an enum describing value type */
    public Type type();

    /**
     * Check how many entries or fields are contained in the current value.
     * Useful for arrays and objects; anything else always returns 0.
     * @return number of entries/fields contained.
     **/
    public int children();

    /**
     * Check how many entries are contained in the current value.
     * Useful for arrays; anything else always returns 0.
     * @return number of entries contained.
     **/
    public int entries();

    /**
     * Check how many fields are contained in the current value.
     * Useful for objects; anything else always returns 0.
     * @return number of fields contained.
     **/
    public int fields();

    /** the current value (for booleans); default: false */
    public boolean asBool();

    /** the current value (for integers); default: 0 */
    public long asLong();

    /** the current value (for floating-point values); default: 0.0 */
    public double asDouble();

    /** the current value (for string values); default: empty string */
    public String asString();

    /** the current value encoded into UTF-8 (for string values); default: empty array */
    public byte[] asUtf8();

    /** the current value (for data values); default: empty array */
    public byte[] asData();

    /**
     * Use the visitor pattern to resolve the underlying type of this value.
     * @param v the visitor
     **/
    public void accept(Visitor v);

    /**
     * Traverse an array value, performing callbacks for each entry.
     *
     * If the current Inspector is connected to an array value,
     * perform callbacks to the given traverser for each entry
     * contained in the array.
     * @param at traverser callback object.
     **/
    @SuppressWarnings("overloads")
    public void traverse(ArrayTraverser at);

    /**
     * Traverse an object value, performing callbacks for each field.
     *
     * If the current Inspector is connected to an object value,
     * perform callbacks to the given traverser for each field
     * contained in the object.
     * @param ot traverser callback object.
     **/
    @SuppressWarnings("overloads")
    public void traverse(ObjectSymbolTraverser ot);

    /**
     * Traverse an object value, performing callbacks for each field.
     *
     * If the current Inspector is connected to an object value,
     * perform callbacks to the given traverser for each field
     * contained in the object.
     * @param ot traverser callback object.
     **/
    @SuppressWarnings("overloads")
    public void traverse(ObjectTraverser ot);

    /**
     * Access an array entry.
     *
     * If the current Inspector doesn't connect to an array value,
     * or the given array index is out of bounds, the returned
     * Inspector will be invalid.
     * @param idx array index.
     * @return a new Inspector for the entry value.
     **/
    public Inspector entry(int idx);

    /**
     * Access an field in an object by symbol id.
     *
     * If the current Inspector doesn't connect to an object value, or
     * the object value does not contain a field with the given symbol
     * id, the returned Inspector will be invalid.
     * @param sym symbol id.
     * @return a new Inspector for the field value.
     **/
    public Inspector field(int sym);

    /**
     * Access an field in an object by symbol name.
     *
     * If the current Inspector doesn't connect to an object value, or
     * the object value does not contain a field with the given symbol
     * name, the returned Inspector will be invalid.
     * @param name symbol name.
     * @return a new Inspector for the field value.
     **/
    public Inspector field(String name);
}
