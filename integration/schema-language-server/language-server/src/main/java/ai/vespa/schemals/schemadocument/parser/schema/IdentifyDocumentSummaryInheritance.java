package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.inheritsDocumentSummary;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * IdentifyDocumentSummaryInheritance identifies a document-summary inheritance and adds it to a list to resolve later
 */
public class IdentifyDocumentSummaryInheritance extends Identifier<SchemaNode> {

	public IdentifyDocumentSummaryInheritance(ParseContext context) {
		super(context);
	}

	@Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.isASTInstance(identifierWithDashStr.class)) return;
        if (node.getParent() == null || !node.getParent().isASTInstance(inheritsDocumentSummary.class)) return;

        if (!node.hasSymbol()) {
            return;
        }

        context.addUnresolvedInheritanceNode(node);
        return;
	}
}
