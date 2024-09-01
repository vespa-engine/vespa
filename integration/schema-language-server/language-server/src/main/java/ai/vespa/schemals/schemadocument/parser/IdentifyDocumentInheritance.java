package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Identify document inherance and add it to a list to resolve later
 */
public class IdentifyDocumentInheritance extends Identifier {

	public IdentifyDocumentInheritance(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isSchemaASTInstance(identifierStr.class)) return ret;
        if (node.getParent() == null || !node.getParent().isSchemaASTInstance(inheritsDocument.class)) return ret;

        if (!node.hasSymbol()) {
            ret.add(new SchemaDiagnostic.Builder()
                        .setRange(node.getRange())
                        .setMessage("Should be symbol")
                        .setSeverity(DiagnosticSeverity.Warning)
                        .build());
        }

        this.context.addUnresolvedInheritanceNode(node);

        return ret;
	}
}
