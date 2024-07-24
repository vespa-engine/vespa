package ai.vespa.schemals.schemadocument.parser;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public abstract class Identifier {

    protected ParseContext context;

    public Identifier(ParseContext context) {
        this.context = context;
    }

    public abstract List<Diagnostic> identify(SchemaNode node);
}
