package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.onnxModelInSchema;
import ai.vespa.schemals.parser.rankingexpression.ast.rankPropertyFeature;
import ai.vespa.schemals.parser.rankingexpression.ast.unaryFunctionName;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.BuiltInFunctions;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.GenericFunction;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.Node.LanguageType;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode.RankNodeType;

/**
 * RankExpressionSymbolResolver goes through unresolved symbols in rank expression, to check if they are calling built in functions and tries
 * to figure out which symbolType the symbol referes to.
 */
public class RankExpressionSymbolResolver { 

    /**
     * Resolves rank expression references in the tree
     *
     * @param node        The schema node to resolve the rank expression references in.
     * @param context     The parse context.
     */
    public static void resolveRankExpressionReferences(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {
        if (node.getLanguageType() == LanguageType.RANK_EXPRESSION) {
            resolveRankExpression(node, context, diagnostics);
        } else {
            for (Node child : node) {
                resolveRankExpressionReferences(child.getSchemaNode(), context, diagnostics);
            }
        }
    }

    public static void resolveRankExpression(SchemaNode schemaNode, ParseContext context, List<Diagnostic> diagnostics) {
        List<RankNode> rankNodes = RankNode.createTree(schemaNode);

        for (RankNode node : rankNodes) {
            traverseRankExpressionTree(node, context, diagnostics);
        }
    }

    private static void traverseRankExpressionTree(RankNode node, ParseContext context, List<Diagnostic> diagnostics) {
        for (RankNode child : node) {
            traverseRankExpressionTree(child, context, diagnostics);
        }

        // All feature nodes has a symbol before the traversal
        if (node.hasSymbol()) {
            if (node.getSymbolStatus() == SymbolStatus.UNRESOLVED) {
                resolveReference(node, context, diagnostics);
            }

            if (node.getSymbolStatus() == SymbolStatus.UNRESOLVED) {
                findBuiltInTensorFunction(node);
            }

            if (node.getSymbolStatus() == SymbolStatus.UNRESOLVED) {
                if (node.getSchemaNode().getParent().isASTInstance(rankPropertyFeature.class)) {
                    findRankPropertyFeature(node, context, diagnostics);
                } else {
                    findBuiltInFunction(node, context, diagnostics);
                }
            }
        }
    }

    public static final Set<Class<?>> builtInTokenizedFunctions = new HashSet<>() {{
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
        // walk down first-child path and remove the first symbol found 
        // because some branches of the tree look like a long chain
        while (true) {
            if (node.hasSymbol()) {
                Symbol symbol = node.getSymbol();
                if (symbol.getStatus() == SymbolStatus.REFERENCE) {
                    context.schemaIndex().deleteSymbolReference(symbol);
                }
                node.removeSymbol();
                return;
            }
            if (node.size() > 0)node = node.get(0).getSchemaNode();
            else break;
        }
    }

    private static void findBuiltInFunction(RankNode node, ParseContext context, List<Diagnostic> diagnostics) {
        if (node.getType() != RankNodeType.FEATURE) {
            return;
        }

        if (node.getSchemaNode().getParent().isASTInstance(rankPropertyFeature.class)) {
            return;
        }

        String identifier = node.getSymbol().getShortIdentifier();

        GenericFunction functionHandler = BuiltInFunctions.rankExpressionBuiltInFunctions.get(identifier);
        if (functionHandler == null) return;
        
        node.getSymbol().setType(SymbolType.FUNCTION);
        node.getSymbol().setStatus(SymbolStatus.BUILTIN_REFERENCE);

        Optional<SchemaNode> functionProperty = node.getProperty();
        functionProperty.ifPresent(property -> removeSymbolFromIndex(context, property));

        diagnostics.addAll(functionHandler.handleArgumentList(context, node, false));
    }

    private static void findRankPropertyFeature(RankNode node, ParseContext context, List<Diagnostic> diagnostics) {
        String identifier = node.getSymbol().getShortIdentifier();

        GenericFunction functionHandler = BuiltInFunctions.rankExpressionBuiltInFunctions.get(identifier);
        if (functionHandler == null) {
            // If no builtin feature is found, the property doesn't really have any effect.
            // Remove symbol reference, but display a warning
            removeSymbolFromIndex(context, node.getSchemaNode());

            Range range = node.getRange();

            // Limit range to first identifier if possible to avoid crashing diagnostics
            if (node.getSchemaNode().size() > 0)
                range = node.getSchemaNode().get(0).getRange();

            diagnostics.add(new SchemaDiagnostic.Builder()
                    .setRange(range)
                    .setMessage("No feature with the name " + identifier + " was found. Property has no effect.")
                    .setSeverity(DiagnosticSeverity.Warning)
                    .build());

            return;
        }

        node.getSymbol().setType(SymbolType.FUNCTION);
        node.getSymbol().setStatus(SymbolStatus.BUILTIN_REFERENCE);

        // TODO: Check valid config properties
        Optional<SchemaNode> functionProperty = node.getProperty();
        functionProperty.ifPresent(property -> removeSymbolFromIndex(context, property));

        diagnostics.addAll(functionHandler.handleArgumentList(context, node, true));
    }

    private static final List<SymbolType> possibleTypes = new ArrayList<>() {{
        // add(SymbolType.PARAMETER); // This is a special case
        add(SymbolType.FUNCTION);
        add(SymbolType.RANK_CONSTANT);
        add(SymbolType.TENSOR_DIMENSION_MAPPED);
        add(SymbolType.TENSOR_DIMENSION_INDEXED);
    }};

    private static boolean isInsideOnnxModelInSchema(Node node) {
        return CSTUtils.findASTClassAncestor(node, onnxModelInSchema.class) != null;
    }

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

        if (isInsideOnnxModelInSchema(referenceNode.getSchemaNode())) {
            // onnx-model blocks outside any rank-profile are problematic,
            // because they can reference functions from a rank-profile (outside scope).
            // We don't have any good way of handling it, but onnx-model outside a profile 
            // is deprecated anyways.
            reference.setStatus(SymbolStatus.INVALID);
            return;
        }

        if (!referenceNode.getArgumentListExists()) {
            // This can be a parameter
            definition = context.schemaIndex().findSymbol(reference.getScope(), SymbolType.PARAMETER, reference.getShortIdentifier());
        }

        // If the symbol isn't a parameter, maybe it is a function
        if (definition.isEmpty()) {
            // NOTE: Seems like a name collision between a parameter and a constants leads to undefined behaviour
            for (SymbolType possibleType : possibleTypes) {
                definition = context.schemaIndex().findSymbol(reference.getScope(), possibleType, reference.getShortIdentifier());

                // If this is a ambiguous reference to a function or a constant, the app doesn't deploy. Therefore a break will not be a problem.
                // TODO: Implement error message for the error above.
                if (definition.isPresent()) {
                    break;
                }
            }
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
