package ai.vespa.schemals.tree;

import java.io.PrintStream;

public interface Visitor {

    private static <T> T traverseCST(SchemaNode node, PrintStream logger) {
        return null;
    }
}