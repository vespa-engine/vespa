// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.searchdefinition.document.HnswIndexParams;

/**
 * This class holds the extracted information after parsing an "index"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
class ParsedIndex {

    private final String name;

    ParsedIndex(String name) {
        this.name = name;
    }

    void addAlias(String alias) {}
    void setArity(int arity) {}
    void setDensePostingListThreshold(double threshold) {}
    void setEnableBm25(boolean value) {}
    void setHnswIndexParams(HnswIndexParams params) {}
    void setLowerBound(long bound) {}
    void setPrefix(boolean value) {}
    void setStemming(String stemming) {}
    void setUpperBound(long bound) {}
}
