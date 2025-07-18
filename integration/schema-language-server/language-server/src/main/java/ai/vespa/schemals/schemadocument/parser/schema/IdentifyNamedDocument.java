package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * IdentifyNamedDocument verifies that a document has the same name as the schema the document is part of.
 * 
 * Should run after symbol definition identifiers
 */
public class IdentifyNamedDocument extends Identifier<SchemaNode> {

	public IdentifyNamedDocument(ParseContext context) {
		super(context);
	}

	@Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.isASTInstance(documentElm.class)) return;

        if (node.size() < 2 || !node.get(1).getSchemaNode().isASTInstance(identifierStr.class)) return;

        Range identifierRange = node.get(1).getRange();
        String documentName = node.get(1).getText();
        Optional<Symbol> schemaSymbol = context.schemaIndex().getSchemaDefinition(documentName);
        if (schemaSymbol.isEmpty() || !schemaSymbol.get().getShortIdentifier().equals(documentName)) {
            // TODO: Quickfix
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(identifierRange)
                .setMessage("Invalid document name \"" + documentName + "\". The document name must match the containing schema's name.")
                .setSeverity(DiagnosticSeverity.Error)
                .setCode(DiagnosticCode.DOCUMENT_NAME_SAME_AS_SCHEMA)
                .build());
        }
	}
}
