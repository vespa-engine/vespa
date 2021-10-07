// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Interface for read-write access to any value or object that is part
 * of a Slime.  All accessors (including meta-data) are inherited from
 * the Inspector interface.  The navigational methods also work the
 * same, except that they return a new Cursor for contained values and
 * sub-structures, to permit writes to embedded values.
 *
 * The write operations are adding a new entry (to arrays), or setting
 * a field value (for objects).  If adding an entry or setting a field
 * cannot be performed for any reason, an invalid Cursor is returned.
 *
 * This could happen because the current cursor is invalid, or it's
 * not connected to an array value (for add methods), or it's not
 * connected to an object (for set methods).  Also note that you can
 * only set() a field once; you cannot overwrite the field in any way.
 **/
public interface Cursor extends Inspector {

    /**
     * Access an array entry.
     *
     * If the current Cursor doesn't connect to an array value,
     * or the given array index is out of bounds, the returned
     * Cursor will be invalid.
     * @param idx array index.
     * @return a new Cursor for the entry value.
     **/
    @Override
    public Cursor entry(int idx);

    /**
     * Access an field in an object by symbol id.
     *
     * If the current Cursor doesn't connect to an object value, or
     * the object value does not contain a field with the given symbol
     * id, the returned Cursor will be invalid.
     * @param sym symbol id.
     * @return a new Cursor for the field value.
     **/
    @Override
    public Cursor field(int sym);

    /**
     * Access an field in an object by symbol name.
     *
     * If the current Cursor doesn't connect to an object value, or
     * the object value does not contain a field with the given symbol
     * name, the returned Cursor will be invalid.
     * @param name symbol name.
     * @return a new Cursor for the field value.
     **/
    @Override
    public Cursor field(String name);

    /**
     * Append an array entry containing a new value of NIX type.
     * Returns an invalid Cursor if unsuccessful.
     * @return a valid Cursor referencing the new entry value if successful.
     **/
    public Cursor addNix();

    /**
     * Append an array entry containing a new value of BOOL type.
     * Returns an invalid Cursor if unsuccessful.
     * @param bit the actual boolean value for initializing a new BoolValue.
     * @return a valid Cursor referencing the new entry value if successful.
     **/
    public Cursor addBool(boolean bit);

    /** add a new entry of LONG type to an array */
    public Cursor addLong(long l);

    /** add a new entry of DOUBLE type to an array */
    public Cursor addDouble(double d);

    /** add a new entry of STRING type to an array */
    public Cursor addString(String str);

    /** add a new entry of STRING type to an array */
    public Cursor addString(byte[] utf8);

    /** add a new entry of DATA type to an array */
    public Cursor addData(byte[] data);

    /**
     * Append an array entry containing a new value of ARRAY type.
     * Returns a valid Cursor (thay may again be used for adding new
     * sub-array entries) referencing the new entry value if
     * successful; otherwise returns an invalid Cursor.
     * @return new Cursor for the new entry value
     **/
    public Cursor addArray();

    /**
     * Append an array entry containing a new value of OBJECT type.
     * Returns a valid Cursor (thay may again be used for setting
     * sub-fields inside the new object) referencing the new entry
     * value if successful; otherwise returns an invalid Cursor.
     * @return new Cursor for the new entry value
     **/
    public Cursor addObject();

    /**
     * Set a field (identified with a symbol id) to contain a new
     * value of NIX type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param sym symbol id for the field to be set
     * @return new Cursor for the new field value
     **/
    public Cursor setNix(int sym);

    /**
     * Set a field (identified with a symbol id) to contain a new
     * value of BOOL type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param sym symbol id for the field to be set
     * @param bit the actual boolean value for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setBool(int sym, boolean bit);

    /**
     * Set a field (identified with a symbol id) to contain a new
     * value of BOOL type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param sym symbol id for the field to be set
     * @param l the actual long value for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setLong(int sym, long l);

    /**
     * Set a field (identified with a symbol id) to contain a new
     * value of BOOL type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param sym symbol id for the field to be set
     * @param d the actual double value for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setDouble(int sym, double d);

    /**
     * Set a field (identified with a symbol id) to contain a new
     * value of BOOL type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param sym symbol id for the field to be set
     * @param str the actual string for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setString(int sym, String str);

    /**
     * Set a field (identified with a symbol id) to contain a new
     * value of BOOL type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param sym symbol id for the field to be set
     * @param utf8 the actual string (encoded as UTF-8 data) for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setString(int sym, byte[] utf8);

    /**
     * Set a field (identified with a symbol id) to contain a new
     * value of BOOL type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param sym symbol id for the field to be set
     * @param data the actual data to be put into the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setData(int sym, byte[] data);

    /**
     * Set a field (identified with a symbol id) to contain a new
     * value of ARRAY type.  Returns a valid Cursor (thay may again be
     * used for adding new array entries) referencing the new field
     * value if successful; otherwise returns an invalid Cursor.
     * @param sym symbol id for the field to be set
     * @return new Cursor for the new field value
     **/
    public Cursor setArray(int sym);

    /**
     * Set a field (identified with a symbol id) to contain a new
     * value of OBJECT type.  Returns a valid Cursor (thay may again
     * be used for setting sub-fields inside the new object)
     * referencing the new field value if successful; otherwise
     * returns an invalid Cursor.
     * @param sym symbol id for the field to be set
     * @return new Cursor for the new field value
     **/
    public Cursor setObject(int sym);

    /**
     * Set a field (identified with a symbol name) to contain a new
     * value of NIX type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param name symbol name for the field to be set
     * @return new Cursor for the new field value
     **/
    public Cursor setNix(String name);

    /**
     * Set a field (identified with a symbol name) to contain a new
     * value of BOOL type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param name symbol name for the field to be set
     * @param bit the actual boolean value for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setBool(String name, boolean bit);

    /**
     * Set a field (identified with a symbol name) to contain a new
     * value of LONG type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param name symbol name for the field to be set
     * @param l the actual long value for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setLong(String name, long l);

    /**
     * Set a field (identified with a symbol name) to contain a new
     * value of DOUBLE type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param name symbol name for the field to be set
     * @param d the actual double value for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setDouble(String name, double d);

    /**
     * Set a field (identified with a symbol name) to contain a new
     * value of STRING type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param name symbol name for the field to be set
     * @param str the actual string for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setString(String name, String str);

    /**
     * Set a field (identified with a symbol name) to contain a new
     * value of STRING type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param name symbol name for the field to be set
     * @param utf8 the actual string (encoded as UTF-8 data) for the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setString(String name, byte[] utf8);

    /**
     * Set a field (identified with a symbol name) to contain a new
     * value of DATA type.  Returns a valid Cursor referencing the new
     * field value if successful; otherwise returns an invalid Cursor.
     * @param name symbol name for the field to be set
     * @param data the actual data to be put into the new field
     * @return new Cursor for the new field value
     **/
    public Cursor setData(String name, byte[] data);

    /**
     * Set a field (identified with a symbol name) to contain a new
     * value of ARRAY type.  Returns a valid Cursor (thay may again be
     * used for adding new array entries) referencing the new field
     * value if successful; otherwise returns an invalid Cursor.
     * @param name symbol name for the field to be set
     * @return new Cursor for the new field value
     **/
    public Cursor setArray(String name);

    /**
     * Set a field (identified with a symbol name) to contain a new
     * value of OBJECT type.  Returns a valid Cursor (thay may again
     * be used for setting sub-fields inside the new object)
     * referencing the new field value if successful; otherwise
     * returns an invalid Cursor.
     * @param name symbol name for the field to be set
     * @return new Cursor for the new field value
     **/
    public Cursor setObject(String name);
}
