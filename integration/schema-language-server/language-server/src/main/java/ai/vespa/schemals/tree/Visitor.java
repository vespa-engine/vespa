package ai.vespa.schemals.tree;

import java.io.PrintStream;

import ai.vespa.schemals.parser.*;

public interface Visitor {

    private static <T> T traverseCST(Node node, PrintStream logger) {
        return null;
    }
}