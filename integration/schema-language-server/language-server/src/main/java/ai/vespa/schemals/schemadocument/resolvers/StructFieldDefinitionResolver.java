package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.STRUCT_FIELD;
import ai.vespa.schemals.parser.ast.fieldBodyElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.structFieldBodyElm;
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * StructFieldDefinitionResolver
 * Finds struct-field declarations and turns them into definitions of fields
 */
public class StructFieldDefinitionResolver {
    public static List<Diagnostic> resolve(ParseContext context, SchemaNode CST) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        traverse(context, CST, diagnostics);
        return diagnostics;
    }

    private static void traverse(ParseContext context, SchemaNode node, List<Diagnostic> diagnostics) {
        if (node.getParent() != null && node.isASTInstance(identifierStr.class) && node.getParent().getASTClass() == structFieldElm.class) {
            handleStructField(context, node, diagnostics);
        }
        for (Node child : node) {
            traverse(context, child.getSchemaNode(), diagnostics);
        }
    }

    private static void handleStructField(ParseContext context, SchemaNode node, List<Diagnostic> diagnostics) {
        Node prev = node.getPreviousSibling();
        if (prev.getASTClass() == STRUCT_FIELD.class) {
            Node enclosingBodyNode = node.getParent().getParent();

            if (enclosingBodyNode.getASTClass() == fieldBodyElm.class) {
                // struct-field declared inside field
                Node fieldIdentifierNode = enclosingBodyNode.getParent().get(1);

                if (!fieldIdentifierNode.hasSymbol() || fieldIdentifierNode.getSymbol().getStatus() != SymbolStatus.DEFINITION) return;
                Optional<Symbol> subfieldInParent = SymbolReferenceResolver.resolveSubFieldReference(node, fieldIdentifierNode.getSymbol(), context, diagnostics);

                if (subfieldInParent.isEmpty()) return;

                Symbol scope = fieldIdentifierNode.getSymbol();
                createStructFieldDefinition(context, scope, node, subfieldInParent.get());
            } else if (enclosingBodyNode.getASTClass() == structFieldBodyElm.class) {
                // nested struct-field
                // the referred field definition is the last component in the struct-field identifier
                Node lastStructFieldIdentifier = enclosingBodyNode.getParent().get(1);
                while (lastStructFieldIdentifier.getNextSibling() != null && lastStructFieldIdentifier.getNextSibling().getASTClass() == identifierStr.class) {
                    lastStructFieldIdentifier = lastStructFieldIdentifier.getNextSibling();
                }
                if (!lastStructFieldIdentifier.hasSymbol() || lastStructFieldIdentifier.getSymbol().getStatus() != SymbolStatus.DEFINITION) return;

                Optional<Symbol> referredParentFieldDefinition = context.schemaIndex().getFirstSymbolDefinition(lastStructFieldIdentifier.getSymbol());

                if (referredParentFieldDefinition.isEmpty()) return;

                Optional<Symbol> subfieldInParent = SymbolReferenceResolver.resolveSubFieldReference(node, referredParentFieldDefinition.get(), context, diagnostics);

                if (subfieldInParent.isEmpty()) return;

                Symbol scope = lastStructFieldIdentifier.getSymbol();
                createStructFieldDefinition(context, scope, node, subfieldInParent.get());

            }
        } else if (prev.hasSymbol() && prev.getSymbol().getStatus() == SymbolStatus.DEFINITION) {
            // Assume we are in a struct-field statement
            Optional<Symbol> referredParentFieldDefinition = context.schemaIndex().getFirstSymbolDefinition(prev.getSymbol());

            if (referredParentFieldDefinition.isEmpty()) return;

            Optional<Symbol> subfieldInParent = SymbolReferenceResolver.resolveSubFieldReference(node, referredParentFieldDefinition.get(), context, diagnostics);

            if (subfieldInParent.isEmpty()) return;

            Symbol scope = prev.getSymbol();
            createStructFieldDefinition(context, scope, node, subfieldInParent.get());
        }
    }

    private static void createStructFieldDefinition(ParseContext context, Symbol scope, SchemaNode node, Symbol referenceToField) {
        node.setSymbolStatus(SymbolStatus.DEFINITION);
        node.setSymbolType(SymbolType.FIELD);
        node.setSymbolScope(scope);
        context.schemaIndex().insertSymbolDefinition(node.getSymbol());
        context.schemaIndex().insertSymbolReference(referenceToField, node.getSymbol());
    }
}
