package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.unaryFunctionName;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.FunctionHandler;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.BuiltInFunctions;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;
import ai.vespa.schemals.tree.rankingexpression.RankNode.RankNodeType;
import ai.vespa.schemals.tree.rankingexpression.RankNode.ReturnType;

public class RankExpressionSymbolResolver { 

    /**
     * Resolves rank expression references in the tree
     *
     * @param node        The schema node to resolve the rank expression references in.
     * @param context     The parse context.
     */
    public static List<Diagnostic> resolveRankExpressionReferences(SchemaNode node, ParseContext context) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (node.getLanguageType() == LanguageType.RANK_EXPRESSION) {

            diagnostics.addAll(resolveRankExpression(node, context));

        } else {
            for (SchemaNode child : node) {
                diagnostics.addAll(resolveRankExpressionReferences(child, context));
            }
        }

        return diagnostics;
    }

    public static List<Diagnostic> resolveRankExpression(SchemaNode schemaNode, ParseContext context) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        Optional<RankNode> rankNode = RankNode.createTree(schemaNode);
        if (rankNode.isEmpty()) {
            // TODO: error message
            return diagnostics;
        }

        RankingExpressionUtils.printTree(context.logger(), rankNode.get());

        return traverseRankExpressionTree(rankNode.get(), context);
    }

    private static List<Diagnostic> traverseRankExpressionTree(RankNode node, ParseContext context) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        for (RankNode child : node) {
            diagnostics.addAll(traverseRankExpressionTree(child, context));
        }

        // All feature nodes has a symbol before the parse
        if (node.hasSymbol()) {

            if (node.getSymbolStatus() == SymbolStatus.UNRESOLVED) {
                resolveReference(node, context, diagnostics);
            }

            if (node.getSymbolStatus() == SymbolStatus.UNRESOLVED) {
                findBuiltInTensorFunction(node);
            }

            if (node.getSymbolStatus() == SymbolStatus.UNRESOLVED) {
                findBuiltInFunction(node, context, diagnostics);
            }

        }

        return diagnostics;
    }

    public static final Set<Class<?>> builInTokenizedFunctions = new HashSet<>() {{
        add(unaryFunctionName.class);
    }};

    private static void findBuiltInTensorFunction(RankNode node) {

        if (node.getType() == RankNodeType.BUILT_IN_FUNCTION) {
            Symbol symbol = node.getSymbol();
            symbol.setType(SymbolType.FUNCTION);
            symbol.setStatus(SymbolStatus.BUILTIN_REFERENCE);
        }
    }

    private static void removeSymbolFromIndex(ParseContext context, SchemaNode node) {
        do {
            if (node.hasSymbol()) {
                Symbol symbol = node.getSymbol();
                if (symbol.getStatus() == SymbolStatus.REFERENCE) {
                    context.schemaIndex().deleteSymbolReference(symbol);
                }
                node.removeSymbol();
            }
            node = node.get(0);
        } while (node.size() > 0);
    }

    private static void findBuiltInFunction(RankNode node, ParseContext context, List<Diagnostic> diagnostics) {
        if (node.getType() != RankNodeType.FEATURE) {
            return;
        }

        String identifier = node.getSymbol().getShortIdentifier();

        FunctionHandler functionHandler = BuiltInFunctions.rankExpressionBultInFunctions.get(identifier);
        if (functionHandler == null) return;
        
        node.setReturnType(ReturnType.DOUBLE);
        node.getSymbol().setType(SymbolType.FUNCTION);
        node.getSymbol().setStatus(SymbolStatus.BUILTIN_REFERENCE);

        Optional<SchemaNode> functionProperty = node.getProperty();
        if (functionProperty.isPresent()) {
            removeSymbolFromIndex(context, functionProperty.get());
        }

        diagnostics.addAll(functionHandler.handleArgumentList(context, node));
    }

    // private static final List<SymbolType> possibleTypes = new ArrayList<>() {{
    //     add(SymbolType.FUNCTION);
    //     add(SymbolType.PARAMETER);
    // }};

    private static void resolveReference(RankNode referenceNode, ParseContext context, List<Diagnostic> diagnostics) {

        if (referenceNode.getSymbolType() != SymbolType.TYPE_UNKNOWN) {
            return;
        }

        if (referenceNode.getInsideLambdaFunction()) {
            resolveReferenceInsideLambda(referenceNode, context, diagnostics);
            return;
        }

        Symbol reference = referenceNode.getSymbol();

        Optional<Symbol> definition = Optional.empty();

        if (!referenceNode.getArgumentListExists()) {
            // This can be a parameter
            definition = context.schemaIndex().findSymbol(reference.getScope(), SymbolType.PARAMETER, reference.getShortIdentifier());
        }

        // If the symbol isn't a parameter, maybe it is a function
        if (definition.isEmpty()) {
            definition = context.schemaIndex().findSymbol(reference.getScope(), SymbolType.FUNCTION, reference.getShortIdentifier());
        }

        if (definition.isEmpty()) {
            return;
        }

        reference.setType(definition.get().getType());
        reference.setStatus(SymbolStatus.REFERENCE);
        context.schemaIndex().insertSymbolReference(definition.get(), reference);
    }

    private static void resolveReferenceInsideLambda(RankNode node, ParseContext context, List<Diagnostic> diagnostics) {
        
        Symbol symbol = node.getSymbol();

        List<Symbol> possibleDefinition = context.schemaIndex().findSymbolsInScope(symbol.getScope(), SymbolType.PARAMETER, symbol.getShortIdentifier());

        if (possibleDefinition.size() == 0) {
            // Symbol not found
            return;
        }

        if (possibleDefinition.size() > 1) {
            return;
        }

        symbol.setType(SymbolType.PARAMETER);
        symbol.setStatus(SymbolStatus.REFERENCE);
        context.schemaIndex().insertSymbolReference(possibleDefinition.get(0), symbol);
    }
}
