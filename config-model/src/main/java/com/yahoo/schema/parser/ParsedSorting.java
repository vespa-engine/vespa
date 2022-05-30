
package com.yahoo.schema.parser;

import com.yahoo.schema.document.Sorting.Function;
import com.yahoo.schema.document.Sorting.Strength;

import java.util.Optional;

/**
 * This class holds the extracted information after parsing a "sorting"
 * block, using simple data structures as far as possible.  Do not put
 * advanced logic here!
 * @author arnej27959
 **/
class ParsedSorting extends ParsedBlock {

    private boolean ascending = true;
    private Function sortFunction = null;
    private Strength sortStrength = null;
    private String sortLocale = null;

    ParsedSorting(String blockName, String blockType) {
        super(blockName, blockType);
    }

    boolean getAscending() { return this.ascending; }
    boolean getDescending() { return ! this.ascending; }
    Optional<Function> getFunction() { return Optional.ofNullable(sortFunction); }
    Optional<Strength> getStrength() { return Optional.ofNullable(sortStrength); }
    Optional<String> getLocale() { return Optional.ofNullable(sortLocale); }

    void setAscending() { this.ascending = true; }

    void setDescending() { this.ascending = false; }

    void setLocale(String value) {
        verifyThat(sortLocale == null, "sorting already has locale", sortLocale);
        this.sortLocale = value;
    }
    void setFunction(Function value) {
        verifyThat(sortFunction == null, "sorting already has function", sortFunction);
        this.sortFunction = value;
    }
    void setStrength(Strength value) {
        verifyThat(sortStrength == null, "sorting already has strength", sortStrength);
        this.sortStrength = value;
    }
}
