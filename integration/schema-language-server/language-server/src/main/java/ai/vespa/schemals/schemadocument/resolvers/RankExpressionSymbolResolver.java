package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.xerces.impl.xs.SchemaGrammar.Schema4Annotations;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.args;
import ai.vespa.schemals.parser.rankingexpression.ast.expression;
import ai.vespa.schemals.parser.rankingexpression.ast.outs;
import ai.vespa.schemals.parser.rankingexpression.ast.unaryFunctionName;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.FunctionHandler;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.FunctionSignature;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.GenericFunction;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.DistanceFunction;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.ExpressionArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.IntegerArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.StringArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;
import ai.vespa.schemals.tree.rankingexpression.RankNode.RankNodeType;
import ai.vespa.schemals.tree.rankingexpression.RankNode.ReturnType;

public class RankExpressionSymbolResolver {

    public static final Map<String, FunctionHandler> rankExpressionBultInFunctions = new HashMap<>() {{
        // ==== Query features ====
        put("query", GenericFunction.singleSymbolArugmnet(SymbolType.QUERY_INPUT));
        put("term", new GenericFunction(new IntegerArgument(), new HashSet<>() {{
            add("significance");
            add("weight");
            add("connectedness");
        }}));
        put("queryTermCount", new GenericFunction());
        
        // ==== Document features ====
        put("fieldLength", GenericFunction.singleSymbolArugmnet(SymbolType.FIELD));
        put("attribute", new GenericFunction(new ArrayList<>() {{
            add(new FunctionSignature(new SymbolArgument(SymbolType.FIELD), new HashSet<>() {{
                add("");
                add("count");
            }}));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new SymbolArgument(SymbolType.FIELD));
                add(new IntegerArgument());
            }}));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new SymbolArgument(SymbolType.FIELD));
                add(new StringArgument("key"));
            }}, new HashSet<>() {{
                add("weight");
                add("contains");
            }}));
        }}));

        // ==== Field match features - normalized ====
        put("fieldMatch", new GenericFunction(new SymbolArgument(SymbolType.FIELD), new HashSet<>() {{
            add("");
            add("proximity");
            add("completeness");
            add("queryCompleteness");
            add("fieldCompleteness");
            add("orderness");
            add("relatedness");
            add("earliness");
            add("longestSequenceRatio");
            add("seqmentProximity");
            add("unweightedProximity");
            add("absoluteProximity");
            add("occurrence");
            add("absoluteOccurrence");
            add("weightedOccureence");
            add("weightedAbsoluteOccurence");
            add("significantOccurence");
        
        // ==== Feild match features - normalized and relative to the whole query ====
            add("weight");
            add("significance");
            add("importance");
        
        // ==== Field matche features - not normalized ====
            add("segments");
            add("matches");
            add("degradedMatches");
            add("outOfOrder");
            add("gaps");
            add("gapLength");
            add("longestSequence");
            add("head");
            add("tail");
            add("segmentDistance");
        }}));

        // ==== Query and field similarity ====
        put("textSimilarity", new GenericFunction(new SymbolArgument(SymbolType.FIELD), new HashSet<>() {{
            add("");
            add("proximity");
            add("order");
            add("queryCoverage");
            add("fieldCoverage");
        }}));

        // ==== Query term and field match features ====
        put("fieldTermMatch", new GenericFunction(new FunctionSignature(new ArrayList<>() {{
            add(new SymbolArgument(SymbolType.FIELD));
            add(new IntegerArgument());
        }}, new HashSet<>() {{
            add("firstPosition");
            add("occurences");
        }})));
        put("matchCount", GenericFunction.singleSymbolArugmnet(SymbolType.FIELD));
        put("matches", new GenericFunction(new ArrayList<>() {{
            add(new FunctionSignature(new SymbolArgument(SymbolType.FIELD)));
            add(new FunctionSignature(new ArrayList<>() {{
                add(new SymbolArgument(SymbolType.FIELD));
                add(new IntegerArgument());
            }}));
        }}));
        put("termDistance", new GenericFunction(new FunctionSignature(new ArrayList<>() {{
            add(new SymbolArgument(SymbolType.FIELD));
            add(new ExpressionArgument("x"));
            add(new ExpressionArgument("y"));
        }}, new HashSet<>() {{
            add("forward");
            add("forwardTermPosition");
            add("reverse");
            add("reverseTermPosition");
        }})));




        
        // ==== Rank score ====
        put("bm25", GenericFunction.singleSymbolArugmnet(SymbolType.FIELD));
        put("nativeRank", new GenericFunction(new ArrayList<>() {{
            add(new FunctionSignature());
            add(FunctionSignature.singleSymbolSignature(SymbolType.FIELD)); // TODO: support unlimited number of fields
        }}));



        // ==== Global features ====
        put("globalSequence", new GenericFunction());
        put("now", new GenericFunction());
        put("random", new GenericFunction());
        put("random.match", new GenericFunction());



        put("distance", new DistanceFunction());
    }};

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

        FunctionHandler functionHandler = rankExpressionBultInFunctions.get(identifier);
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
