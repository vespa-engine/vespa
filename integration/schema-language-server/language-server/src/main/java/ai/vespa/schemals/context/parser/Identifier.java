package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.tree.SchemaNode;

public abstract class Identifier {

    protected PrintStream logger;

    public Identifier(PrintStream logger) {
        this.logger = logger;
    }

    public abstract ArrayList<Diagnostic> identify(SchemaNode node);
}
