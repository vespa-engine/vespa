package ai.vespa.schemals.lsp.completion.provider;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.expression;
import ai.vespa.schemals.parser.ast.featureListElm;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;

/**
 * RankingExpressionCompletion
 * We only perform very simple function name completion.
 */
public class RankingExpressionCompletion implements CompletionProvider {

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

	@Override
	public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        SchemaNode clean = CSTUtils.getLastCleanNode(context.document.getRootNode(), context.position);

        if (matchFunctionCompletion(context,clean)) {
            Optional<Symbol> scope = CSTUtils.findScope(clean);

            List<CompletionItem> result = new ArrayList<>();

            if (scope.isPresent()) {
                Symbol scopeIterator = scope.get();
                while (scopeIterator != null && scopeIterator.getType() != SymbolType.RANK_PROFILE)scopeIterator = scopeIterator.getScope();
                List<Symbol> userDefinedFunctions = context.schemaIndex.listSymbolsInScope(scopeIterator, EnumSet.of(SymbolType.FUNCTION));

                result.addAll(
                    userDefinedFunctions.stream()
                    .map(symbol -> 
                        CompletionUtils.constructFunction(symbol.getShortIdentifier(), getFunctionSignature(symbol), symbol.getPrettyIdentifier())
                    ).toList()
                );
            }

            return result;
        }

        return List.of();
	}
}
