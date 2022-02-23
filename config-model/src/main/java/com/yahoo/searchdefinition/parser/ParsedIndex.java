// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.searchdefinition.document.HnswIndexParams;
import com.yahoo.searchdefinition.document.Stemming;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class holds the extracted information after parsing an "index"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
class ParsedIndex {

    private final String name;
    private boolean enableBm25 = false;
    private boolean isPrefix = false;
    private HnswIndexParams hnswParams;
    private final List<String> aliases = new ArrayList<>();
    private Optional<Stemming> stemming = Optional.empty();
    private Integer arity;
    private Long lowerBound;
    private Long upperBound;
    private Double densePLT;
    
    ParsedIndex(String name) {
        this.name = name;
    }

    String name() { return this.name; }
    boolean getEnableBm25() { return this.enableBm25; }
    boolean getPrefix() { return this.isPrefix; }
    HnswIndexParams getHnswIndexParams() { return this.hnswParams; }
    List<String> getAliases() { return ImmutableList.copyOf(aliases); }
    boolean hasStemming() { return stemming.isPresent(); }
    Stemming getStemming() { return stemming.get(); }
    Integer getArity() { return this.arity; }
    Long getLowerBound() { return this.lowerBound; }
    Long getUpperBound() { return this.upperBound; }
    Double getDensePostingListThreshold() { return this.densePLT; }
    

    void addAlias(String alias) {
        aliases.add(alias);
    }

    void setArity(int arity) {
        this.arity = arity;
    }

    void setDensePostingListThreshold(double threshold) {
        this.densePLT = threshold;
    }

    void setEnableBm25(boolean value) {
        this.enableBm25 = value;
    }

    void setHnswIndexParams(HnswIndexParams params) {
        this.hnswParams = params;
    }

    void setLowerBound(long value) {
        this.lowerBound = value;
    }

    void setPrefix(boolean value) {
        this.isPrefix = value;
    }

    void setStemming(Stemming stemming) {
        this.stemming = Optional.of(stemming);
    }

    void setUpperBound(long value) {
        this.upperBound = value;
    }
}
