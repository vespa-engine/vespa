package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public abstract class Identifier {

    protected ParseContext context;

    public Identifier(ParseContext context) {
        this.context = context;
    }

    public abstract ArrayList<Diagnostic> identify(SchemaNode node);
}
