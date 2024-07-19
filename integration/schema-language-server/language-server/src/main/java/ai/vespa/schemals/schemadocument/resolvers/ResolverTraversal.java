package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

/**
 * ResolverTraversal
 * Traversing the CST after initial parsing step and inheritance has been resolved to resolve symbol references etc.
 */
public class ResolverTraversal {
    public static List<Diagnostic> traverse(ParseContext context, SchemaNode CST) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        traverse(context, CST, diagnostics);
        return diagnostics;
    }

    private static void traverse(ParseContext context, SchemaNode currentNode, List<Diagnostic> diagnostics) {
        if (currentNode.hasSymbol() && currentNode.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
            SymbolReferenceResolver.resolveSymbolReference(currentNode, context, diagnostics);
        }

        if (currentNode.containsOtherLanguageData(LanguageType.INDEXING)) {
            IndexingLanguageResolver.resolveIndexingLanguage(context, currentNode, diagnostics);
        }

        // Language resolvers should be responsible for traversing their own language
        if (currentNode.getLanguageType() == LanguageType.SCHEMA) {
            for (SchemaNode child : currentNode) {
                traverse(context, child, diagnostics);
            }
        }

        // Some things need run after the children has run.
        // If it becomes a lot, put into its own file
        // TODO: solution for field in struct
        if (currentNode.isASTInstance(fieldElm.class)) {
            ValidateFieldSettings.validateFieldSettings(context, currentNode, diagnostics);
            SchemaNode fieldIdentifierNode = currentNode.get(1);
            if (fieldIdentifierNode.hasSymbol() && fieldIdentifierNode.getSymbol().getType() == SymbolType.FIELD && fieldIdentifierNode.getSymbol().getStatus() == SymbolStatus.DEFINITION) {
                if (fieldIdentifierNode.getNextSibling() != null 
                        && fieldIdentifierNode.getNextSibling().getNextSibling() != null  
                        && fieldIdentifierNode.getNextSibling().getNextSibling().isASTInstance(dataType.class)) {
                    // check if it is a document reference
                    dataType dataTypeNode = (dataType)fieldIdentifierNode.getNextSibling().getNextSibling().getOriginalSchemaNode();

                    if (dataTypeNode.getParsedType().getVariant() == Variant.DOCUMENT) {
                        var indexingTypes = context.fieldIndex().getFieldIndexingTypes(fieldIdentifierNode.getSymbol());

                        if (!indexingTypes.contains(IndexingType.ATTRIBUTE)) {
                            // TODO: quickfix
                            diagnostics.add(new Diagnostic(
                                fieldIdentifierNode.getRange(),
                                "Invalid document reference. The field must be an attribute.",
                                DiagnosticSeverity.Error,
                                ""
                            ));
                        }
                    }
                }
            }
        }
    }
}
