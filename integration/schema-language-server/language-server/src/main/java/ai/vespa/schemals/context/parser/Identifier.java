package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public abstract class Identifier {

    protected ParseContext context;

    public Identifier(ParseContext context) {
        this.context = context;
    }

    public abstract ArrayList<Diagnostic> identify(SchemaNode node);
}
