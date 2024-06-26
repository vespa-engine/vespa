package ai.vespa.schemals.parser;

public class ParsedField {
    private String name;

    ParsedField(String name) {
        this.name = name;
    }

    public String toString() {
        return "ParsedField(" + name + ")";
    }
}
