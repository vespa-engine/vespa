package ai.vespa.schemals.schemadocument.resolvers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.IntegerArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.StringArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.SymbolArgument;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

public class RankExpressionSymbolResolver {

    public static final Map<String, FunctionHandler> rankExpressionBultInFunctions = new HashMap<>() {{
        put("query", GenericFunction.singleSymbolArugmnet(SymbolType.QUERY_INPUT));
        put("term", new GenericFunction(new IntegerArgument(), new HashSet<>() {{
            add("significance");
            add("weight");
            add("connectedness");
        }}));
        put("queryTermCount", new GenericFunction());
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
        put("bm25", GenericFunction.singleSymbolArugmnet(SymbolType.FIELD));
        put("distance", new DistanceFunction());
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
            add("weight");
            add("significance");
            add("importance");
            add("segments");
            add("matches");
            add("degradedMatches");
        }}));
    }};

    /**
     * Resolves rank expression references in the tree
     *
     * @param node        The schema node to resolve the rank expression references in.
     * @param context     The parse context.
     */
    public static List<Diagnostic> resolveRankExpressionReferences(SchemaNode node, ParseContext context) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        if (
            node.getLanguageType() == LanguageType.RANK_EXPRESSION &&
            node.hasSymbol()
        ) {
            if (node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
                resolveReference(node.getSymbol(), context, diagnostics);
            }

            if (node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
                findBuiltInTokenFunction(node);
            }

            if (node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
                findSymbolTypeOfBuiltInArgument(node, context, diagnostics);
            }

            // if (node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
            //     node.setSymbolStatus(SymbolStatus.REFERENCE); // TODO: remove later, is placed here to pass tests
            // }

        }

        for (SchemaNode child : node) {
            diagnostics.addAll(resolveRankExpressionReferences(child, context));
        }

        return diagnostics;
    }

    private static List<SchemaNode> findRankExpressionArguments(SchemaNode node) {
        List<SchemaNode> ret = new ArrayList<>();

        SchemaNode parent = node.getParent();

        if (parent == null || parent.size() < 3) return ret;

        SchemaNode argNode = parent.get(2);
        if (!argNode.isASTInstance(args.class)) return ret;

        for (SchemaNode child : argNode) {
            if (child.isASTInstance(expression.class)) {
                ret.add(child);
            }
        }

        return ret;
    }

    private static Optional<SchemaNode> findFunctionPropertyNode(SchemaNode node) {
        SchemaNode parent = node.getParent();

        if (parent == null) return Optional.empty();

        for (SchemaNode child : parent) {
            if (child.isASTInstance(outs.class)) {
                return Optional.of(child);
            }
        }

        return Optional.empty();
    }

    public static final Set<Class<?>> builInTokenizedFunctions = new HashSet<>() {{
        add(unaryFunctionName.class);
    }};

    private static void findBuiltInTokenFunction(SchemaNode node) {

        boolean isBuiltInTokenizedFunction = builInTokenizedFunctions.contains(node.getASTClass());
        if (isBuiltInTokenizedFunction) {
            Symbol symbol = node.getSymbol();
            symbol.setType(SymbolType.FUNCTION);
            symbol.setStatus(SymbolStatus.BUILTIN_REFERENCE);

        }
    }

    private static void removeSymbol(ParseContext context, SchemaNode node) {
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
    
    private static void findSymbolTypeOfBuiltInArgument(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {

        String identifier = node.getSymbol().getShortIdentifier();

        FunctionHandler functionHandler = rankExpressionBultInFunctions.get(identifier);
        if (functionHandler == null) return;
        node.getSymbol().setType(SymbolType.FUNCTION);
        node.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);

        List<SchemaNode> arguments = findRankExpressionArguments(node);
        Optional<SchemaNode> functionProperty = findFunctionPropertyNode(node);

        if (functionProperty.isPresent()) {
            removeSymbol(context, functionProperty.get());
        }

        diagnostics.addAll(functionHandler.handleArgumentList(context, node, arguments, functionProperty));
    }

    private static final List<SymbolType> possibleTypes = new ArrayList<>() {{
        add(SymbolType.FUNCTION);
        add(SymbolType.PARAMETER);
    }};

    private static void resolveReference(Symbol reference, ParseContext context, List<Diagnostic> diagnostics) {

        if (reference.getType() != SymbolType.TYPE_UNKNOWN) {
            return;
        }

        List<Symbol> possibleDefinitions = new ArrayList<>();

        for (SymbolType possibleType : possibleTypes) {
            Optional<Symbol> symbol = context.schemaIndex().findSymbol(reference.getScope(), possibleType, reference.getShortIdentifier());
            if (symbol.isPresent()) {
                possibleDefinitions.add(symbol.get());
            }
        }

        if (possibleDefinitions.size() == 0) {
            return;
        }

        // TODO: filter for away the definitions with large scope

        if (possibleDefinitions.size() > 1) {
            diagnostics.add(new Diagnostic(reference.getLocation().getRange(), "The reference is ambiguous.", DiagnosticSeverity.Error, ""));
            return;
        }

        Symbol definition = possibleDefinitions.get(0);

        reference.setType(definition.getType());
        reference.setStatus(SymbolStatus.REFERENCE);
        context.schemaIndex().insertSymbolReference(definition, reference);
    }
}
