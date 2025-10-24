// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public class ParsedAttribute extends ParsedBlock {

    private boolean enableOnlyBitVector = false;
    private boolean enableFastAccess = false;
    private boolean enableFastRank = false;
    private boolean enableFastSearch = false;
    private boolean enableMutable = false;
    private boolean enablePaged = false;
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private ParsedSorting sortSettings = null;
    private String distanceMetric = null;

    public ParsedAttribute(String name) {
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

    public void addAlias(String from, String to) {
        verifyThat(! aliases.containsKey(to), "already has alias", to);
        aliases.put(to, from);
    }

    public void setDistanceMetric(String value) {
        verifyThat(distanceMetric == null, "already has distance-metric", distanceMetric);
        this.distanceMetric = value;
    }

    public ParsedSorting sortInfo() {
        if (sortSettings == null) sortSettings = new ParsedSorting(name(), "attribute.sorting");
        return this.sortSettings;
    }

    public void setEnableOnlyBitVector(boolean value) { this.enableOnlyBitVector = value; }
    public void setFastAccess(boolean value) { this.enableFastAccess = value; }
    public void setFastRank(boolean value) { this.enableFastRank = value; }
    public void setFastSearch(boolean value) { this.enableFastSearch = value; }
    public void setMutable(boolean value) { this.enableMutable = value; }
    public void setPaged(boolean value) { this.enablePaged = value; }
}
