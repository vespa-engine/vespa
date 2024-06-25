package ai.vespa.schemals.parser;


class ParsedDocument {
    private String name;

    ParsedDocument(String name) {
        this.name = name;
    }

    public String toString() {
        return "ParsedDocument(" + name + ")";
    }
}