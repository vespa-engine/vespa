package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.ArrayList;

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
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isSchemaASTInstance(identifierWithDashStr.class)) return ret;
        if (node.getParent() == null || !node.getParent().getSchemaNode().isSchemaASTInstance(inheritsDocumentSummary.class)) return ret;

        if (!node.hasSymbol()) {
            ret.add(new SchemaDiagnostic.Builder()
                .setRange(node.getRange())
                .setMessage("Should be reference")
                .setSeverity(DiagnosticSeverity.Warning)
                .build());
        }

        context.addUnresolvedInheritanceNode(node);

        return ret;
	}
}
