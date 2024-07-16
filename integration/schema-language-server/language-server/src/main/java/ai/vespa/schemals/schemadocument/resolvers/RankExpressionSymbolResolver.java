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

import com.google.protobuf.Option;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.args;
import ai.vespa.schemals.parser.rankingexpression.ast.expression;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

public class RankExpressionSymbolResolver {
    

    public static final Map<String, SymbolType> rankExpressionBultInFunctions = new HashMap<>() {{
        put("bm25", SymbolType.FIELD);
        put("attribute", SymbolType.FIELD);
        put("query", SymbolType.QUERY_INPUT);
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
            if (node.getSymbol().getStatus() == SymbolStatus.BUILTIN_REFERENCE) {
                findSymbolTypeOfBuiltInArgument(node, context, diagnostics);
            }

            if (node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
                resolveReference(node.getSymbol(), context, diagnostics);
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

    private static void findSymbolTypeOfBuiltInArgument(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {
        String identifier = node.getSymbol().getShortIdentifier();
        SymbolType arguemntType = rankExpressionBultInFunctions.get(identifier);
        if (arguemntType == null) return;

        List<SchemaNode> arguments = findRankExpressionArguments(node);

        for (SchemaNode arg : arguments) {
            SchemaNode symbolNode = arg;
            context.logger().println("ARGUMENT: " + arg);

            while (!symbolNode.hasSymbol() && symbolNode.size() > 0) {
                symbolNode = symbolNode.get(0);
            }

            context.logger().println("SYMBOL: " + symbolNode);

            if (symbolNode.hasSymbol()) {
                Symbol symbol = symbolNode.getSymbol();
                context.logger().println(symbol);
                if (symbol.getStatus() == SymbolStatus.UNRESOLVED && symbol.getType() == SymbolType.TYPE_UNKNOWN) {
                    symbol.setType(arguemntType);
                }
            }
        }
    }

    private static final List<SymbolType> possibleTypes = new ArrayList<>() {{
        add(SymbolType.FUNCTION);
        add(SymbolType.PARAMETER);
    }};

    private static void resolveReference(Symbol reference, ParseContext context, List<Diagnostic> diagnostics) {
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
