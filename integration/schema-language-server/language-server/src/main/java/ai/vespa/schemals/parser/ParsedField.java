package ai.vespa.schemals.parser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ParsedField extends ParsedBlock {

    private ParsedType type;
    private boolean hasBolding = false;
    private boolean isFilter = false;
    private int overrideId = 0;
    private boolean isLiteral = false;
    private boolean isNormal = false;
    private Integer weight;
    private String normalizing = null;
    private final ParsedMatchSettings matchInfo = new ParsedMatchSettings();
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<String, String> rankTypes = new LinkedHashMap<>();
    private final Map<String, ParsedField> structFields = new LinkedHashMap<>();
    private final List<String> queryCommands = new ArrayList<>();

    ParsedField(String name, ParsedType type) {
        super(name, "field");
        this.type = type;
    }

    ParsedType getType() { return this.type; }
    boolean hasBolding() { return this.hasBolding; }
    boolean hasFilter() { return this.isFilter; }
    boolean hasLiteral() { return this.isLiteral; }
    boolean hasNormal() { return this.isNormal; }
    boolean hasIdOverride() { return overrideId != 0; }
    int idOverride() { return overrideId; }
    List<ParsedField> getStructFields() { return List.copyOf(structFields.values()); }
    List<String> getAliases() { return List.copyOf(aliases.keySet()); }
    List<String> getQueryCommands() { return List.copyOf(queryCommands); }
    String lookupAliasedFrom(String alias) { return aliases.get(alias); }
    ParsedMatchSettings matchSettings() { return this.matchInfo; }
    Optional<Integer> getWeight() { return Optional.ofNullable(weight); }
    Optional<String> getNormalizing() { return Optional.ofNullable(normalizing); }
    Map<String, String> getRankTypes() { return Collections.unmodifiableMap(rankTypes); }

    void setBolding(boolean value) { this.hasBolding = value; }
    void setFilter(boolean value) { this.isFilter = value; }
    void setId(int id) { this.overrideId = id; }
    void setLiteral(boolean value) { this.isLiteral = value; }
    void setNormal(boolean value) { this.isNormal = value; }
    void setNormalizing(String value) { this.normalizing = value; }
    void setWeight(int weight) { this.weight = weight; }


    void addAlias(String from, String to) {
        verifyThat(! aliases.containsKey(to), "already has alias", to);
        aliases.put(to, from);
    }
    
}
