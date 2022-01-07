// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * A mapping from an arbitrary set of unique strings to a range of
 * integers.  Slime users normally won't need to use this class
 * directly.
 */
final class SymbolTable {

    public static final int INVALID = Integer.MAX_VALUE;

    private static final int[] emptyHash = new int[1];

    private int capacity = 0;
    private int hashSize() { return (capacity + (capacity >> 1) - 1); }
    private int used = 0;
    private String[] names;
    private int[] hash = emptyHash;

    private final void rehash() {
        if (capacity == 0) {
            capacity = 32;
            names = new String[capacity];
            hash = new int[hashSize() + (capacity << 1)];
            return;
        }
        capacity = (capacity << 1);
        String[] n = names;
        names = new String[capacity];
        System.arraycopy(n, 0, names, 0, used);
        hash = new int[hashSize() + (capacity << 1)];
        for (int i = 0; i < used; i++) {
            int prev = Math.abs(names[i].hashCode() % hashSize());
            int entry = hash[prev];
            while (entry != 0) {
                prev = entry + 1;
                entry = hash[prev];
            }
            final int insertIdx = (hashSize() + (i << 1));
            hash[prev] = insertIdx;
            hash[insertIdx] = i;
        }
    }

    /** Return count of contained symbol names. */
    final int symbols() { return used; }

    /**
     * Return the symbol name associated with an id.
     * @param symbol the id, must be in range [0, symbols()-1]
     **/
    final String inspect(int symbol) { return names[symbol]; }

    /**
     * Add a name to the symbol table; if the name is already
     * in the symbol table just returns the id it already had.
     * @param name the name to insert
     * @return the id now associated with the name
     **/
    final int insert(String name) {
        if (used == capacity) {
            rehash();
        }
        int prev = Math.abs(name.hashCode() % hashSize());
        int entry = hash[prev];
        while (entry != 0) {
            final int sym = hash[entry];
            if (names[sym].equals(name)) { // found entry
                return sym;
            }
            prev = entry + 1;
            entry = hash[prev];
        }
        final int insertIdx = (hashSize() + (used << 1));
        hash[prev] = insertIdx;
        hash[insertIdx] = used;
        names[used++] = name;
        return (used - 1);
    }

    /**
     * Find the id associated with a symbol name; if the
     * name was not in the symbol table returns the
     * INVALID constant instead.
     **/
    final int lookup(String name) {
        int entry = hash[Math.abs(name.hashCode() % hashSize())];
        while (entry != 0) {
            final int sym = hash[entry];
            if (names[sym].equals(name)) { // found entry
                return sym;
            }
            entry = hash[entry + 1];
        }
        return INVALID;
    }

}
