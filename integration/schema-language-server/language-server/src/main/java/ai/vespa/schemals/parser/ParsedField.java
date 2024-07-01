package ai.vespa.schemals.parser;


public class ParsedField extends ParsedBlock {

    private ParsedType type;

    ParsedField(String name, ParsedType type) {
        super(name, "field");
        this.type = type;
    }

}
