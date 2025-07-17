package ai.vespa.schemals.schemadocument.parser;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.Node;

/**
 * Mark all dirty nodes as Syntax error
 */
public class IdentifyDirtyNodes<T extends Node> extends Identifier<T> {

    public IdentifyDirtyNodes(ParseContext context) {
		super(context);
	}

    @Override
    public void identify(T node, List<Diagnostic> diagnostics) {
        if (
            node.getIsDirty() &&
            node.isLeaf()
        ) {
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(node.getRange())
                .setMessage("Invalid syntax.")
                .setSeverity(DiagnosticSeverity.Error)
                .build());
        }
    }
}
