// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.index;

/**
 * Represents an entry in a posting list, containing an integer id and integer data reference.
 *
 * @author Magnar Nedland
 */
public class Posting implements Comparable<Posting> {

    private final int id;
    private final int dataRef;

    public Posting(int id, int dataRef) {
        this.id = id;
        this.dataRef = dataRef;
    }

    public int getId() {
        return id;
    }

    public int getDataRef() {
        return dataRef;
    }

    @Override
    public int compareTo(Posting o) {
        return Integer.compareUnsigned(id, o.id);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Posting posting = (Posting) o;

        if (id != posting.id) return false;
        return dataRef == posting.dataRef;

    }

    @Override
    public int hashCode() {
        int result = id;
        result = 31 * result + dataRef;
        return result;
    }

}
