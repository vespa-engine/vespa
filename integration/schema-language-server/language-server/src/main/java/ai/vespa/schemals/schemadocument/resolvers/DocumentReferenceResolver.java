package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * DocumentReferenceResolver
 * Run after symbol references has been resolved
 */
public class DocumentReferenceResolver {
    public static List<Diagnostic> resolveDocumentReferences(ParseContext context) {

        List<Diagnostic> diagnostics = new ArrayList<>();

        Optional<Symbol> myDocumentDefinition = context.schemaIndex().getSchemaDefinition(context.scheduler().getSchemaDocument(context.fileURI()).getSchemaIdentifier());

        if (myDocumentDefinition.isEmpty()) return diagnostics;

        // TODO: check for fields with attribute indexing

        for (SchemaNode documentReferenceNode : context.unresolvedDocumentReferenceNodes()) {
            // If it has not been resolved as a reference, we don't bother
            if (!documentReferenceNode.hasSymbol() || documentReferenceNode.getSymbol().getStatus() != SymbolStatus.REFERENCE) continue;

            Optional<Symbol> referencedDocument = context.schemaIndex().getSymbolDefinition(documentReferenceNode.getSymbol());

            if (referencedDocument.isEmpty()) continue;

            if (!context.schemaIndex().tryRegisterDocumentReference(myDocumentDefinition.get(), referencedDocument.get())) {
                diagnostics.add(new Diagnostic(
                    documentReferenceNode.getRange(),
                    "Cannot reference document " + documentReferenceNode.getText() + " because cyclic references are not allowed",
                    DiagnosticSeverity.Error,
                    ""
                ));
            }
        }

        context.clearUnresolvedDocumentReferenceNodes();
        return diagnostics;
    }
}
