// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Top-level value class that contains one Value data object and a
 * symbol table (shared between all directly or indirectly contained
 * ObjectValue data objects).
 * 
 * @author havardpe
 */
public final class Slime {

    private final SymbolTable names = new SymbolTable();
    private Value root = NixValue.instance();

    SymbolTable symbolTable() { return names; }

    /**
     * Construct an empty Slime with an empty top-level value.
     */
    public Slime() {}

    /** Returns a count of names in the symbol table. */
    public int symbols() {
        return names.symbols();
    }

    /**
     * Return the symbol name associated with an id.
     * 
     * @param symbol the id, must be in range [0, symbols()-1]
     */
    public String inspect(int symbol) {
        return names.inspect(symbol);
    }

    /**
     * Add a name to the symbol table; if the name is already
     * in the symbol table just returns the id it already had.
     * 
     * @param name the name to insert
     * @return the id now associated with the name
     */
    public int insert(String name) {
        return names.insert(name);
    }

    /**
     * Find the id associated with a symbol name; if the
     * name was not in the symbol table returns the
     * constant Integer.MAX_VALUE instead.
     */
    public int lookup(String name) {
        return names.lookup(name);
    }

    /** Get a Cursor connected to the top-level data object. */
    public Cursor get() { return root; }

    /**
     * Create a new empty value and make it the new top-level data object.
     */
    public Cursor setNix() {
        root = NixValue.instance();
        return root;
    }

    /**
     * Create a new boolean value and make it the new top-level data object.
     * 
     * @param bit the actual boolean value for the new value
     */
    public Cursor setBool(boolean bit) {
        root = BoolValue.instance(bit);
        return root;
    }

    /**
     * Create a new double value and make it the new top-level data object.
     * 
     * @param l the actual long value for the new value
     */
    public Cursor setLong(long l) {
        root = new LongValue(l);
        return root;
    }

    /**
     * Create a new double value and make it the new top-level data object.
     * 
     * @param d the actual double value for the new value
     */
    public Cursor setDouble(double d) {
        root = new DoubleValue(d);
        return root;
    }

    /**
     * Create a new string value and make it the new top-level data object.
     * 
     * @param str the actual string for the new value
     */
    public Cursor setString(String str) {
        root = StringValue.create(str);
        return root;
    }

    /**
     * Create a new string value and make it the new top-level data object.
     * 
     * @param utf8 the actual string (encoded as UTF-8 data) for the new value
     */
    public Cursor setString(byte[] utf8) {
        root = Utf8Value.create(utf8);
        return root;
    }

    /**
     * Create a new data value and make it the new top-level data object.
     * 
     * @param data the actual data to be put into the new value.
     */
    public Cursor setData(byte[] data) {
        root = DataValue.create(data);
        return root;
    }

    /**
     * Create a new array value and make it the new top-level data object.
     */
    public Cursor setArray() {
        root = new ArrayValue(names);
        return root;
    }

    /**
     * Create a new object value and make it the new top-level data object.
     */
    public Cursor setObject() {
        root = new ObjectValue(names);
        return root;
    }

    /**
     * Take the current top-level data object and make it a field in a
     * new ObjectValue with the given symbol id as field id; the new
     * ObjectValue will also become the new top-level data object.
     */
    public Cursor wrap(int sym) {
        root = new ObjectValue(names, sym, root);
        return root;
    }

    /**
     * Take the current top-level data object and make it a field in a
     * new ObjectValue with the given symbol name as field name; the new
     * ObjectValue will also become the new top-level data object.
     */
    public Cursor wrap(String name) {
        return wrap(names.insert(name));
    }

    /**
     * Tests whether the two Inspectors are equal.
     *
     * <p>Since equality of two Inspectors is subtle, {@link Object#equals(Object)} is not used.</p>
     *
     * @return true if they are equal.
     */
    public boolean equalTo(Slime that) {
        return get().equalTo(that.get());
    }

    @Override
    public String toString() {
        return get().toString();
    }
}
