package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.rootSchemaItem;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Identify a schema without a document, and sends an error if no document was found inside the schema
 */
public class IdentifyDocumentlessSchema extends Identifier<SchemaNode> {

	public IdentifyDocumentlessSchema(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();
        if (!node.isSchemaASTInstance(rootSchema.class))return ret;

        if (node.size() < 2 || node.get(1).getIsDirty()) {
            // Schema has bad syntax. Missing mandatory document would not be helpful
            return ret;
        }

        // Look for document definition inside
        for (SchemaNode child : node) {
            if (!child.isSchemaASTInstance(rootSchemaItem.class)) continue;
            if (child.size() == 0) continue;
            if (child.get(0).isSchemaASTInstance(documentElm.class)) return ret; // Found document
        }

        ret.add(new SchemaDiagnostic.Builder()
            .setRange(node.get(0).getRange())
            .setMessage("Missing mandatory document definition in schema body.")
            .setSeverity(DiagnosticSeverity.Error)
            .setCode(DiagnosticCode.DOCUMENTLESS_SCHEMA)
            .build());
        return ret;
	}
}
