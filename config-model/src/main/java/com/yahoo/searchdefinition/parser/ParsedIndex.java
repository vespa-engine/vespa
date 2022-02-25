// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.searchdefinition.document.HnswIndexParams;
import com.yahoo.searchdefinition.document.Stemming;

import java.util.ArrayList;
import java.util.List;

/**
 * This class holds the extracted information after parsing an "index"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
class ParsedIndex extends ParsedBlock {

    private boolean enableBm25 = false;
    private boolean isPrefix = false;
    private HnswIndexParams hnswParams;
    private final List<String> aliases = new ArrayList<>();
    private Stemming stemming = null;
    private Integer arity;
    private Long lowerBound;
    private Long upperBound;
    private Double densePLT;
    
    ParsedIndex(String name) {
        super(name, "index");
    }

    boolean getEnableBm25() { return this.enableBm25; }
    boolean getPrefix() { return this.isPrefix; }
    HnswIndexParams getHnswIndexParams() { return this.hnswParams; }
    List<String> getAliases() { return List.copyOf(aliases); }
    boolean hasStemming() { return stemming != null; }
    Stemming getStemming() { return stemming; }
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
        this.stemming = stemming;
    }

    void setUpperBound(long value) {
        this.upperBound = value;
    }
}
