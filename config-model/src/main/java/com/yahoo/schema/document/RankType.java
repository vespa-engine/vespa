// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.document;

/**
 * The rank type of a field. For now this is just a container of a string name.
 * This class is immutable.
 *
 * @author  bratseth
 */
public enum RankType {

    /** *implicit* default: No type has been set. */
    DEFAULT,

    // Rank types which can be set explicitly. These are defined for Vespa in NativeRankTypeDefinitionSet
    IDENTITY, ABOUT, TAGS, EMPTY;

    @Override
    public String toString() {
        return "rank type " + name().toLowerCase();
    }

    /**
     * Returns the rank type from a string, regardless of its case.
     *
     * @param  rankTypeName a rank type name in any casing
     * @return the rank type found
     * @throws IllegalArgumentException if not found
     */
    public static RankType fromString(String rankTypeName) {
        try {
            return RankType.valueOf(rankTypeName.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown rank type '" + rankTypeName + "'. Supported rank types are " +
                                               "'identity', 'about', 'tags' and 'empty'.");
        }
    }

}
