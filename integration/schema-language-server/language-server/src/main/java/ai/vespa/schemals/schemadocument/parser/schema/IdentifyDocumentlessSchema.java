package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.rootSchemaItem;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Identify a schema without a document, and sends an error if no document was found inside the schema
 */
public class IdentifyDocumentlessSchema extends Identifier<SchemaNode> {

	public IdentifyDocumentlessSchema(ParseContext context) {
		super(context);
	}

	@Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.isASTInstance(rootSchema.class))return;

        if (node.size() < 2 || node.get(1).getIsDirty()) {
            // Bad syntax
            return;
        }

        // Look for document definition inside
        for (Node child : node) {
            if (!child.isASTInstance(rootSchemaItem.class)) continue;
            if (child.size() == 0) continue;
            if (child.get(0).isASTInstance(documentElm.class)) return; // Found document
        }

        diagnostics.add(new SchemaDiagnostic.Builder()
            .setRange(node.get(0).getRange())
            .setMessage("Missing mandatory document definition in schema body.")
            .setSeverity(DiagnosticSeverity.Error)
            .setCode(DiagnosticCode.DOCUMENTLESS_SCHEMA)
            .build());
	}
}
