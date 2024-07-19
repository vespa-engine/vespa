package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DiagnosticTag;

import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.indexinglanguage.ast.ATTRIBUTE;
import ai.vespa.schemals.parser.indexinglanguage.ast.INDEX;
import ai.vespa.schemals.parser.indexinglanguage.ast.SUMMARY;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

/**
 * IndexingLanguageResolver
 */
public class IndexingLanguageResolver {

    private ParseContext context;
    private Symbol containingFieldDefinition;

    /*
     * Given a node containing an ILSCRIPT, resolve the language
     */
    public static void resolveIndexingLanguage(ParseContext context, SchemaNode indexingLanguageNode, List<Diagnostic> diagnostics) {
        SchemaNode fieldDefinitionNode = indexingLanguageNode.getParent().getParent().get(1);
        while (fieldDefinitionNode.getNextSibling() != null && fieldDefinitionNode.getNextSibling().isASTInstance(identifierStr.class) && fieldDefinitionNode.getNextSibling().hasSymbol()) {
            fieldDefinitionNode = fieldDefinitionNode.getNextSibling();
        }
        Symbol fieldDefinitionSymbol = fieldDefinitionNode.getSymbol();

        if (fieldDefinitionSymbol.getStatus() == SymbolStatus.REFERENCE) {
            Optional<Symbol> definition = context.schemaIndex().getSymbolDefinition(fieldDefinitionSymbol);
            if (definition.isPresent())fieldDefinitionSymbol = definition.get();
        }

        if (fieldDefinitionSymbol.getStatus() != SymbolStatus.DEFINITION || fieldDefinitionSymbol.getType() != SymbolType.FIELD) {
            diagnostics.add(new Diagnostic(
                fieldDefinitionNode.getRange(),
                "Could not find field definition. Indexing language will not be resolved",
                DiagnosticSeverity.Warning,
                ""
            ));
            return;
        }

        IndexingLanguageResolver instance = new IndexingLanguageResolver(context, fieldDefinitionSymbol);
        instance.traverse(indexingLanguageNode, diagnostics);
    }

    private IndexingLanguageResolver(ParseContext context, Symbol containingFieldDefinition) {
        this.context = context;
        this.containingFieldDefinition = containingFieldDefinition;
    }

    private void traverse(SchemaNode node, List<Diagnostic> diagnostics) {

        if (node.isASTInstance(ATTRIBUTE.class)) {
            context.fieldIndex().addFieldIndexingType(containingFieldDefinition, IndexingType.ATTRIBUTE);
        }
        if (node.isASTInstance(INDEX.class)) {
            context.fieldIndex().addFieldIndexingType(containingFieldDefinition, IndexingType.INDEX);
        }
        if (node.isASTInstance(SUMMARY.class)) {
            context.fieldIndex().addFieldIndexingType(containingFieldDefinition, IndexingType.SUMMARY);
        }

        for (SchemaNode child : node) {
            traverse(child, diagnostics);
        }
    }
}
