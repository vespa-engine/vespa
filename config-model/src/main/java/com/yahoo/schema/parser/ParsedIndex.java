// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.schema.document.HnswIndexParams;
import com.yahoo.schema.document.Stemming;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * This class holds the extracted information after parsing an "index"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
class ParsedIndex extends ParsedBlock {

    private Boolean enableBm25 = null;
    private Boolean isPrefix = null;
    private HnswIndexParams hnswParams = null;
    private final List<String> aliases = new ArrayList<>();
    private Stemming stemming = null;
    private Integer arity = null;
    private Long lowerBound = null;
    private Long upperBound = null;
    private Double densePLT = null;
    
    ParsedIndex(String name) {
        super(name, "index");
    }

    Optional<Boolean> getEnableBm25() { return Optional.ofNullable(this.enableBm25); }
    Optional<Boolean> getPrefix() { return Optional.ofNullable(this.isPrefix); }
    Optional<HnswIndexParams> getHnswIndexParams() { return Optional.ofNullable(this.hnswParams); }
    List<String> getAliases() { return List.copyOf(aliases); }
    boolean hasStemming() { return stemming != null; }
    Optional<Stemming> getStemming() { return Optional.ofNullable(stemming); }
    Optional<Integer> getArity() { return Optional.ofNullable(this.arity); }
    Optional<Long> getLowerBound() { return Optional.ofNullable(this.lowerBound); }
    Optional<Long> getUpperBound() { return Optional.ofNullable(this.upperBound); }
    Optional<Double> getDensePostingListThreshold() { return Optional.ofNullable(this.densePLT); }

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
