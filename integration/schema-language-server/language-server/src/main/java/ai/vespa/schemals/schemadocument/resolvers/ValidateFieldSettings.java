package ai.vespa.schemals.schemadocument.resolvers;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * ValidateFieldSettings
 * Run after field children has been resolved
 */
public class ValidateFieldSettings {

    public static void validateFieldSettings(ParseContext context, SchemaNode fieldElmNode, List<Diagnostic> diagnostics) {
        Node fieldIdentifierNode = fieldElmNode.get(1);
        if (!fieldIdentifierNode.hasSymbol() || fieldIdentifierNode.getSymbol().getType() != SymbolType.FIELD || fieldIdentifierNode.getSymbol().getStatus() != SymbolStatus.DEFINITION) return;

        /*
         * Validate reference<document>
         * */
        if (fieldIdentifierNode.getNextSibling() != null 
                && fieldIdentifierNode.getNextSibling().getNextSibling() != null  
                && fieldIdentifierNode.getNextSibling().getNextSibling().getASTClass() == dataType.class) {
            // check if it is a document reference
            dataType dataTypeNode = (dataType)fieldIdentifierNode.getNextSibling().getNextSibling().getSchemaNode().getOriginalSchemaNode();

            if (dataTypeNode.getParsedType() != null && dataTypeNode.getParsedType().getVariant() == Variant.DOCUMENT) {
                var indexingTypes = context.fieldIndex().getFieldIndexingTypes(fieldIdentifierNode.getSymbol());

                if (!indexingTypes.contains(IndexingType.ATTRIBUTE)) {
                    diagnostics.add(new SchemaDiagnostic.Builder()
                            .setRange( fieldIdentifierNode.getRange())
                            .setMessage( "Invalid document reference. The field must be an attribute.")
                            .setSeverity( DiagnosticSeverity.Error)
                            .setCode(DiagnosticCode.DOCUMENT_REFERENCE_ATTRIBUTE)
                            .build() );
                }
            }
        }
    }
}
