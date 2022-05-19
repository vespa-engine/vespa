// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.document.RankType;

import java.util.Collections;
import java.util.Map;

/**
 * A set of rank type definitions used for native rank features.
 *
 * @author geirst
 */
public class NativeRankTypeDefinitionSet {

    /** The name of this rank definition set */
    private String name;

    /** The unmodifiable rank type implementations in this set */
    private final Map<RankType, NativeRankTypeDefinition> typeDefinitions;

    /** Returns the default rank type (about) */
    public static RankType getDefaultRankType() { return RankType.ABOUT; }

    public NativeRankTypeDefinitionSet(String name) {
        this.name = name;

        Map<RankType, NativeRankTypeDefinition> typeDefinitions = new java.util.LinkedHashMap<>();
        typeDefinitions.put(RankType.IDENTITY, createIdentityRankType(RankType.IDENTITY));
        typeDefinitions.put(RankType.ABOUT, createAboutRankType(RankType.ABOUT));
        typeDefinitions.put(RankType.TAGS, createTagsRankType(RankType.TAGS));
        typeDefinitions.put(RankType.EMPTY, createEmptyRankType(RankType.EMPTY));
        this.typeDefinitions = Collections.unmodifiableMap(typeDefinitions);
    }

    private NativeRankTypeDefinition createEmptyRankType(RankType type) {
        NativeRankTypeDefinition rank = new NativeRankTypeDefinition(type);
        rank.addTable(new NativeTable(NativeTable.Type.FIRST_OCCURRENCE, "linear(0,0)"));
        rank.addTable(new NativeTable(NativeTable.Type.OCCURRENCE_COUNT, "linear(0,0)"));
        rank.addTable(new NativeTable(NativeTable.Type.PROXIMITY, "linear(0,0)"));
        rank.addTable(new NativeTable(NativeTable.Type.REVERSE_PROXIMITY, "linear(0,0)"));
        rank.addTable(new NativeTable(NativeTable.Type.WEIGHT, "linear(0,0)"));
        return rank;
    }

    private NativeRankTypeDefinition createAboutRankType(RankType type) {
        NativeRankTypeDefinition rank = new NativeRankTypeDefinition(type);
        rank.addTable(new NativeTable(NativeTable.Type.FIRST_OCCURRENCE, "expdecay(8000,12.50)"));
        rank.addTable(new NativeTable(NativeTable.Type.OCCURRENCE_COUNT, "loggrowth(1500,4000,19)"));
        rank.addTable(new NativeTable(NativeTable.Type.PROXIMITY, "expdecay(500,3)"));
        rank.addTable(new NativeTable(NativeTable.Type.REVERSE_PROXIMITY, "expdecay(400,3)"));
        rank.addTable(new NativeTable(NativeTable.Type.WEIGHT, "linear(1,0)"));
        return rank;
    }

    private NativeRankTypeDefinition createIdentityRankType(RankType type) {
        NativeRankTypeDefinition rank = new NativeRankTypeDefinition(type);
        rank.addTable(new NativeTable(NativeTable.Type.FIRST_OCCURRENCE, "expdecay(100,12.50)"));
        rank.addTable(new NativeTable(NativeTable.Type.OCCURRENCE_COUNT, "loggrowth(1500,4000,19)"));
        rank.addTable(new NativeTable(NativeTable.Type.PROXIMITY, "expdecay(5000,3)"));
        rank.addTable(new NativeTable(NativeTable.Type.REVERSE_PROXIMITY, "expdecay(3000,3)"));
        rank.addTable(new NativeTable(NativeTable.Type.WEIGHT, "linear(1,0)"));
        return rank;
    }

    private NativeRankTypeDefinition createTagsRankType(RankType type) {
        NativeRankTypeDefinition rank = new NativeRankTypeDefinition(type);
        rank.addTable(new NativeTable(NativeTable.Type.FIRST_OCCURRENCE, "expdecay(8000,12.50)"));
        rank.addTable(new NativeTable(NativeTable.Type.OCCURRENCE_COUNT, "loggrowth(1500,4000,19)"));
        rank.addTable(new NativeTable(NativeTable.Type.PROXIMITY, "expdecay(500,3)"));
        rank.addTable(new NativeTable(NativeTable.Type.REVERSE_PROXIMITY, "expdecay(400,3)"));
        rank.addTable(new NativeTable(NativeTable.Type.WEIGHT, "loggrowth(38,50,1)"));
        return rank;
    }

    /**
     * Returns a rank type definition if given an existing rank type name,
     * or null if given a rank type which has no native implementation (meaning somebody forgot to add it),
    */
    public NativeRankTypeDefinition getRankTypeDefinition(RankType type) {
        if (type == RankType.DEFAULT)
            type = getDefaultRankType();
        return typeDefinitions.get(type);
    }

    /** Returns an unmodifiable map of the type definitions in this */
    public Map<RankType, NativeRankTypeDefinition> types() { return typeDefinitions; }

    public String toString() {
        return "native rank type definitions " + name;
    }

}
