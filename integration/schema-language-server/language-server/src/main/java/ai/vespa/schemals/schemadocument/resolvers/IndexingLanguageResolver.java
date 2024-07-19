package ai.vespa.schemals.schemadocument.resolvers;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.indexingElm;
import ai.vespa.schemals.parser.indexinglanguage.ast.ATTRIBUTE;
import ai.vespa.schemals.parser.indexinglanguage.ast.INDEX;
import ai.vespa.schemals.parser.indexinglanguage.ast.SUMMARY;
import ai.vespa.schemals.parser.indexinglanguage.ast.attributeExp;
import ai.vespa.schemals.parser.indexinglanguage.ast.indexExp;
import ai.vespa.schemals.parser.indexinglanguage.ast.script;
import ai.vespa.schemals.parser.indexinglanguage.ast.statement;
import ai.vespa.schemals.parser.indexinglanguage.ast.summaryExp;
import ai.vespa.schemals.tree.SchemaNode;

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

        if (indexingLanguageNode.isASTInstance(indexingElm.class)) {
            var expression = ((indexingElm)indexingLanguageNode.getOriginalSchemaNode()).expression;
            //String inputType = expression.requiredInputType() == null ? "null" : expression.requiredInputType().toString();
            //String outputType = expression.createdOutputType() == null ? "null" : expression.createdOutputType().toString();
            //diagnostics.add(new Diagnostic(indexingLanguageNode.get(0).getRange(), "Input: " + inputType + ", Output: " + outputType, DiagnosticSeverity.Information, ""));

            if (expression != null && expression.requiredInputType() != null && !context.fieldIndex().getIsInsideDoc(fieldDefinitionSymbol)) {
                diagnostics.add(new Diagnostic(
                    indexingLanguageNode.get(0).getRange(), 
                    "Expected " + expression.requiredInputType().getName() + " input, but no input is specified. Fields defined outside the document must start with indexing statements explicitly collecting input.", 
                    DiagnosticSeverity.Error, 
                ""));
            }
        }

        IndexingLanguageResolver instance = new IndexingLanguageResolver(context, fieldDefinitionSymbol);
        instance.traverse(indexingLanguageNode, diagnostics);
    }

    private IndexingLanguageResolver(ParseContext context, Symbol containingFieldDefinition) {
        this.context = context;
        this.containingFieldDefinition = containingFieldDefinition;
    }

    private void traverse(SchemaNode node, List<Diagnostic> diagnostics) {

        if (node.isASTInstance(ATTRIBUTE.class) && !node.getParent().isASTInstance(attributeExp.class)) {
            context.fieldIndex().addFieldIndexingType(containingFieldDefinition, IndexingType.ATTRIBUTE);
        }
        if (node.isASTInstance(INDEX.class) && !node.getParent().isASTInstance(indexExp.class)) {
            context.fieldIndex().addFieldIndexingType(containingFieldDefinition, IndexingType.INDEX);
        }
        if (node.isASTInstance(SUMMARY.class) && !node.getParent().isASTInstance(summaryExp.class)) {
            context.fieldIndex().addFieldIndexingType(containingFieldDefinition, IndexingType.SUMMARY);
        }

        if (node.isASTInstance(statement.class) 
                && (node.getParent().isASTInstance(indexingElm.class) || node.getParent().isASTInstance(script.class))) {
            // Top level statement of field outside document should have some input
            statement originalNode = (statement)node.getOriginalIndexingNode();
            StatementExpression expression = originalNode.expression;
            //String inputType = expression.requiredInputType() == null ? "null" : expression.requiredInputType().toString();
            //String outputType = expression.createdOutputType() == null ? "null" : expression.createdOutputType().toString();
            //diagnostics.add(new Diagnostic(node.getRange(), "Input: " + inputType + ", Output: " + outputType, DiagnosticSeverity.Information, ""));

            if (expression != null && expression.requiredInputType() != null && !context.fieldIndex().getIsInsideDoc(containingFieldDefinition)) {
                diagnostics.add(new Diagnostic(node.getRange(), "Expected " + expression.requiredInputType().getName() + " input, but no input is specified. Fields defined outside the document must start with indexing statements explicitly collecting input.", DiagnosticSeverity.Error, ""));
            }
        }

        for (SchemaNode child : node) {
            traverse(child, diagnostics);
        }
    }
}
