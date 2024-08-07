package ai.vespa.schemals.lsp.completion.provider;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.completion.utils.CompletionUtils;
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
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * RankingExpressionCompletion
 * Somewhat basic completion of user defined function names and builtin ranking features and their properties
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
        SchemaNode clean = CSTUtils.getLastCleanNode(context.document.getRootNode(), context.position);

        List<CompletionItem> result = new ArrayList<>();
        if (matchFunctionCompletion(context,clean)) {

            result.addAll(getUserDefinedFunctions(context, clean));
            result.addAll(builtinFunctionCompletions);

        } else {
            result.addAll(getFunctionPropertyCompletion(context, CSTUtils.getNodeAtPosition(context.document.getRootNode(), context.startOfWord())));
        }
        return result;
	}


    private boolean matchFunctionCompletion(EventCompletionContext context, SchemaNode clean) {
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
        SchemaNode definitionNode = functionDefinition.getNode();
        //signature.append(definitionNode.getText());
        SchemaNode it = definitionNode.getNextSibling();
        do {
            signature.append(it.getText());
            it = it.getNextSibling();
        } while(it != null && !it.isASTInstance(NL.class) && !it.isASTInstance(openLbrace.class));
        //signature.append(")");
        return signature.toString();
    }

    List<CompletionItem> getUserDefinedFunctions(EventCompletionContext context, SchemaNode node) {
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

    List<CompletionItem> getFunctionPropertyCompletion(EventCompletionContext context, SchemaNode startOfWordNode) {
        if (context.triggerCharacter != '.') return List.of();
        SchemaNode featureNode = CSTUtils.findASTClassAncestor(startOfWordNode, feature.class);
        if (featureNode == null || featureNode.size() == 0) {
            return List.of();
        }
        if (featureNode.getRankNode().isEmpty()) {
            return List.of();
        }

        RankNode rankNode = featureNode.getRankNode().get();

        Optional<SpecificFunction> functionSignature = rankNode.getFunctionSignature();
        if (functionSignature.isEmpty()) {
            return List.of();
        }

        FunctionSignature signature = functionSignature.get().getSignature();

        List<CompletionItem> result = new ArrayList<>();

        for (String prop : signature.getProperties()) {
            if (prop.isBlank()) continue;
            result.add(CompletionUtils.constructBasic(prop));
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
        // first argument keyword
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
