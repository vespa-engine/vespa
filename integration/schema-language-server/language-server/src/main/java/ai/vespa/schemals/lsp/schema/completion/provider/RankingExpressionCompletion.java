package ai.vespa.schemals.lsp.schema.completion.provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Hover;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.schema.completion.utils.CompletionUtils;
import ai.vespa.schemals.lsp.schema.hover.SchemaHover;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.expression;
import ai.vespa.schemals.parser.ast.featureListElm;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.parser.rankingexpression.ast.feature;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.BuiltInFunctions;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.FunctionSignature;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.GenericFunction;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.SpecificFunction;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.Argument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.EnumArgument;
import ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.KeywordArgument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Node.LanguageType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * RankingExpressionCompletion
 * Completion of user defined function names and builtin ranking features and their properties.
 * TODO: more context aware completion, per now the completions are the same everywhere in a ranking expression or feature list.
 */
public class RankingExpressionCompletion implements CompletionProvider {
    /**
     * Compute completion snippets for all the builtin ranking features using the signatures
     * in {@link BuiltInFunctions}.
     */
    private final static List<CompletionItem> builtinFunctionCompletions = new ArrayList<>() {{
        Set<String> addedNames = new HashSet<>();
        for (var entry : BuiltInFunctions.rankExpressionBuiltInFunctions.entrySet()) {
            if (!(entry.getValue() instanceof GenericFunction)) continue;

            GenericFunction function = (GenericFunction)entry.getValue();
            String name = entry.getKey();

            for (List<FunctionSignature> group : groupSignatures(function.getSignatures())) {
                StringBuilder signatureStr = new StringBuilder("(");
                signatureStr.append(String.join(", ", group.get(0).getArgumentList().stream().map(arg -> arg.displayString()).toList()));
                signatureStr.append(")");
                CompletionItem item = CompletionUtils.constructFunction(name, signatureStr.toString(), "builtin");
                item.setInsertText(buildGroupInsertText(name, group));

                SpecificFunction specificFunction = new SpecificFunction((GenericFunction)entry.getValue(), group.get(0));
                Optional<Hover> hover = SchemaHover.getRankFeatureHover(specificFunction);

                if (hover.isPresent() && hover.get().getContents().isRight()) {
                    item.setDocumentation(hover.get().getContents().getRight());
                }

                this.add(item);
            }
            addedNames.add(name);
        }

        for (String function : BuiltInFunctions.simpleBuiltInFunctionsSet) {
            if (addedNames.contains(function))continue;
            this.add(CompletionUtils.constructFunction(function, "()", "builtin"));
        }
    }};

	@Override
	public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        Node clean = CSTUtils.getLastCleanNode(context.document.getRootNode(), context.position);
        if (clean == null) return List.of();

        List<CompletionItem> result = new ArrayList<>();
        if (matchFunctionCompletion(context,clean)) {

            result.addAll(getUserDefinedFunctions(context, clean));
            result.addAll(builtinFunctionCompletions);

        } else {
            result.addAll(getFunctionPropertyCompletion(context, CSTUtils.getNodeAtPosition(context.document.getRootNode(), context.startOfWord())));
        }
        return result;
	}


    private boolean matchFunctionCompletion(EventCompletionContext context, Node clean) {
        if (context.triggerCharacter.equals('.')) return false;
        return (clean.getLanguageType() == LanguageType.RANK_EXPRESSION || (
            clean.getParent() != null && (
                clean.getParent().isASTInstance(expression.class) 
             || clean.getParent().isASTInstance(featureListElm.class)
             )
        ));
    }

    String getFunctionSignature(Symbol functionDefinition) {
        StringBuilder signature = new StringBuilder();
        Node definitionNode = functionDefinition.getNode();
        //signature.append(definitionNode.getText());
        Node it = definitionNode.getNextSibling();
        do {
            signature.append(it.getText());
            it = it.getNextSibling();
        } while(it != null && !it.isASTInstance(NL.class) && !it.isASTInstance(openLbrace.class));
        //signature.append(")");
        return signature.toString();
    }

    List<CompletionItem> getUserDefinedFunctions(EventCompletionContext context, Node node) {
        Optional<Symbol> scope = CSTUtils.findScope(node);
        if (scope.isEmpty())return List.of();

        Symbol scopeIterator = scope.get();

        Symbol myFunction = null;

        while (scopeIterator != null && scopeIterator.getType() != SymbolType.RANK_PROFILE) {
            // cannot use user defined functions in lambda
            if (scopeIterator.getType() == SymbolType.LAMBDA_FUNCTION) return List.of();

            if (scopeIterator.getType() == SymbolType.FUNCTION)
                myFunction = scopeIterator;

            scopeIterator = scopeIterator.getScope();
        }
        if (scopeIterator == null) return List.of();

        List<CompletionItem> ret = new ArrayList<>();

        for (Symbol symbol : context.schemaIndex.listSymbolsInScope(scopeIterator, SymbolType.FUNCTION)) {
            if (symbol.equals(myFunction)) continue;
            ret.add(
                CompletionUtils.constructFunction(symbol.getShortIdentifier(), getFunctionSignature(symbol), symbol.getPrettyIdentifier())
            );
        }
        return ret;
    }

    List<CompletionItem> getFunctionPropertyCompletion(EventCompletionContext context, Node startOfWordNode) {
        if (context.triggerCharacter != '.') return List.of();
        Node rawNode = CSTUtils.findASTClassAncestor(startOfWordNode, feature.class);
        if (rawNode == null || rawNode.size() == 0 || !rawNode.isSchemaNode()) {
            return List.of();
        }
        SchemaNode featureNode = rawNode.getSchemaNode();
        if (featureNode.getRankNode().isEmpty()) {
            return List.of();
        }

        RankNode rankNode = featureNode.getRankNode().get();

        Optional<SpecificFunction> functionSignature = rankNode.getFunctionSignature();
        if (functionSignature.isEmpty()) {
            return List.of();
        }

        SpecificFunction specificFunction = functionSignature.get();
        FunctionSignature signature = functionSignature.get().getSignature();

        List<CompletionItem> result = new ArrayList<>();

        for (String prop : signature.getProperties()) {
            if (prop.isBlank()) continue;
            CompletionItem item = CompletionUtils.constructBasic(prop);

            specificFunction.setProperty(prop);

            Optional<Hover> documentationHover = SchemaHover.getRankFeatureHover(specificFunction);

            if (documentationHover.isPresent() && documentationHover.get().getContents().isRight()) {
                item.setDocumentation(documentationHover.get().getContents().getRight());
            }

            result.add(item);

        }
        return result;
    }


    /**
     * Some signatures are identical except differing in the first KeywordArgument,
     * for example closeness(field, name) and closeness(label, name).
     * This function makes groups based on that.
     */
    private static List<List<FunctionSignature>> groupSignatures(List<FunctionSignature> functionSignatures) {
        List<List<FunctionSignature>> ret = new ArrayList<>();
        Set<Integer> skip = new HashSet<>();

        for (int i = 0; i < functionSignatures.size(); ++i) {
            if (skip.contains(i)) continue;

            FunctionSignature current = functionSignatures.get(i);
            if (current.getArgumentList().isEmpty()) {
                ret.add(List.of(current));
                continue;
            }
            if (!(current.getArgumentList().get(0) instanceof KeywordArgument)) {
                ret.add(List.of(current));
                continue;
            }

            List<FunctionSignature> group = new ArrayList<>() {{ add(current); }};

            for (int j = i + 1; j < functionSignatures.size(); ++j) {
                FunctionSignature candidate = functionSignatures.get(j);
                if (candidate.getArgumentList().size() != current.getArgumentList().size()) continue;
                if (!(candidate.getArgumentList().get(0) instanceof KeywordArgument)) continue;
                skip.add(j);
                group.add(candidate);
            }
            ret.add(group);
        }

        return ret;
    }

    private static String buildGroupInsertText(String name, List<FunctionSignature> group) {
        StringBuilder snippet = new StringBuilder()
            .append(name)
            .append("(");

        int startIndex = 0;
        if (group.size() > 1) {
            snippet.append("${1|")
                   .append(String.join(",", group.stream().map(signature -> 
                           ((KeywordArgument)(signature.getArgumentList().get(0))).getArgument()).toList()))
                   .append("|}");
            startIndex = 1;
        }

        for (int i = startIndex; i < group.get(0).getArgumentList().size(); ++i) {
            if (i > 0)snippet.append(", ");

            Argument current = group.get(0).getArgumentList().get(i);
            snippet.append("${")
                   .append(i+1);

            if (current instanceof EnumArgument) {
                snippet.append("|")
                       .append(String.join(",",((EnumArgument)current).getValidArguments()))
                       .append("|}");
            } else {
                snippet.append(":")
                       .append(current.displayString())
                       .append("}");
            }
        }
        snippet.append(")");
        return snippet.toString();
    }

}
