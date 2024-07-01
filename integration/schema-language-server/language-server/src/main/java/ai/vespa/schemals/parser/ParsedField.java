package ai.vespa.schemals.parser;


public class ParsedField extends ParsedBlock {

    private ParsedType type;
    private int overrideId = 0;

    ParsedField(String name, ParsedType type) {
        super(name, "field");
        this.type = type;
    }

    ParsedType getType() { return this.type; }
    boolean hasIdOverride() { return overrideId != 0; }
    int idOverride() { return overrideId; }

    void setId(int id) { this.overrideId = id; }

}
