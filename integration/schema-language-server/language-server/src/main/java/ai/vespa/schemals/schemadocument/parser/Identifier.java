package ai.vespa.schemals.schemadocument.parser;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.Node;

/**
 * Identifier is a abstract class to represent functions to identifiy patterns in the AST and do actions based on those actions.
 */
public abstract class Identifier<T extends Node> {

    protected ParseContext context;

    public Identifier(ParseContext context) {
        this.context = context;
    }

    public abstract List<Diagnostic> identify(T node);
}
