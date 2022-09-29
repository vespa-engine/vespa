// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class holds the extracted information after parsing a
 * "attribute" block, using simple data structures as far as
 * possible.  Do not put advanced logic here!
 * @author arnej27959
 **/
class ParsedAttribute extends ParsedBlock {

    private boolean enableOnlyBitVector = false;
    private boolean enableFastAccess = false;
    private boolean enableFastRank = false;
    private boolean enableFastSearch = false;
    private boolean enableMutable = false;
    private boolean enablePaged = false;
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private ParsedSorting sortSettings = null;
    private String distanceMetric = null;

    ParsedAttribute(String name) {
        super(name, "attribute");
    }

    List<String> getAliases() { return List.copyOf(aliases.keySet()); }
    String lookupAliasedFrom(String alias) { return aliases.get(alias); }
    Optional<String> getDistanceMetric() { return Optional.ofNullable(distanceMetric); }
    boolean getEnableOnlyBitVector() { return this.enableOnlyBitVector; }
    boolean getFastAccess() { return this.enableFastAccess; }
    boolean getFastRank() { return this.enableFastRank; }
    boolean getFastSearch() { return this.enableFastSearch; }
    boolean getMutable() { return this.enableMutable; }
    boolean getPaged() { return this.enablePaged; }
    Optional<ParsedSorting> getSorting() { return Optional.ofNullable(sortSettings); }

    void addAlias(String from, String to) {
        verifyThat(! aliases.containsKey(to), "already has alias", to);
        aliases.put(to, from);
    }

    void setDistanceMetric(String value) {
        verifyThat(distanceMetric == null, "already has distance-metric", distanceMetric);
        this.distanceMetric = value;
    }

    ParsedSorting sortInfo() {
        if (sortSettings == null) sortSettings = new ParsedSorting(name(), "attribute.sorting");
        return this.sortSettings;
    }

    void setEnableOnlyBitVector(boolean value) { this.enableOnlyBitVector = value; }
    void setFastAccess(boolean value) { this.enableFastAccess = true; }
    void setFastRank(boolean value) { this.enableFastRank = true; }
    void setFastSearch(boolean value) { this.enableFastSearch = true; }
    void setMutable(boolean value) { this.enableMutable = true; }
    void setPaged(boolean value) { this.enablePaged = true; }
}
