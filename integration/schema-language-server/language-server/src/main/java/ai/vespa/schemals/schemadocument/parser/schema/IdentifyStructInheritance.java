package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.inheritsStruct;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * IdentifyStructInheritance identifies if a struct inherits from another struct and adds it to a list to resolve later
 */
public class IdentifyStructInheritance extends Identifier<SchemaNode> {

	public IdentifyStructInheritance(ParseContext context) {
		super(context);
	}

	@Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.isASTInstance(identifierStr.class)) return;
        if (node.getParent() == null || !node.getParent().isASTInstance(inheritsStruct.class)) return;

        if (!node.hasSymbol()) {
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(node.getRange())
                .setMessage("Should be reference")
                .setSeverity(DiagnosticSeverity.Warning)
                .build());
        }

        context.addUnresolvedInheritanceNode(node);
	}
}
