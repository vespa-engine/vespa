package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * DocumentReferenceResolver
 * Run after symbol references has been resolved
 */
public class DocumentReferenceResolver {
    public static List<Diagnostic> resolveDocumentReferences(ParseContext context) {

        List<Diagnostic> diagnostics = new ArrayList<>();

        SchemaDocument document = context.scheduler().getSchemaDocument(context.fileURI());
        // TODO: better way to find document definition
        if (document == null) return diagnostics;
        Optional<Symbol> myDocumentDefinition = context.schemaIndex().findSymbol(null, SymbolType.DOCUMENT, document.getSchemaIdentifier());

        if (myDocumentDefinition.isEmpty()) return diagnostics;

        // TODO: check for fields which are attributes (need at least one)

        for (SchemaNode documentReferenceNode : context.unresolvedDocumentReferenceNodes()) {
            // If it has not been resolved as a reference, we don't bother
            if (!documentReferenceNode.hasSymbol() || documentReferenceNode.getSymbol().getStatus() != SymbolStatus.REFERENCE) continue;

            Optional<Symbol> referencedDocument = context.schemaIndex().getSymbolDefinition(documentReferenceNode.getSymbol());

            if (referencedDocument.isEmpty()) continue;

            if (!context.schemaIndex().tryRegisterDocumentReference(myDocumentDefinition.get(), referencedDocument.get())) {
                String message = " because cyclic references are not allowed";
                if (myDocumentDefinition.get().equals(referencedDocument.get())) message = " because self-references are not allowed";
                diagnostics.add(new SchemaDiagnostic.Builder()
                        .setRange( documentReferenceNode.getRange())
                        .setMessage( "Cannot reference document " + documentReferenceNode.getText() + message)
                        .setSeverity( DiagnosticSeverity.Error)
                        .build() );
            }
        }

        context.clearUnresolvedDocumentReferenceNodes();
        return diagnostics;
    }
}
