// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.document.RankType;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * The definition of a rank type used for native rank features.
 *
 * @author geirst
 */
public class NativeRankTypeDefinition {

    /** The type this defines */
    private RankType type;

    /** The rank tables of this rank type */
    private List<NativeTable> rankTables = new java.util.ArrayList<>();

    public NativeRankTypeDefinition(RankType type) {
        this.type = type;
    }

    public RankType getType() {
        return type;
    }

    public void addTable(NativeTable table) {
        rankTables.add(table);
    }

    /** Returns an unmodifiable list of the tables in this type definition */
    public Iterator<NativeTable> rankSettingIterator() {
        return Collections.unmodifiableList(rankTables).iterator();
    }

    public String toString() {
        return "native definition of rank type '" + type + "'";
    }

}
