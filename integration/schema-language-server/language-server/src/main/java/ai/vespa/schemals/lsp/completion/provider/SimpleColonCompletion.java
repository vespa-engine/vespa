package ai.vespa.schemals.lsp.completion.provider;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.completion.provider.FixedKeywordBodies.FixedKeywordBody;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.schemadocument.SchemaDocument;

/**
 * For matching KEYWORD_TOKEN IDENTIFIER? COLON *fixed set of keywords go here*
 */
public class SimpleColonCompletion implements CompletionProvider {

    private final static List<FixedKeywordBody> colonSupporters = List.of(
        FixedKeywordBodies.ATTRIBUTE,
        FixedKeywordBodies.DISTANCE_METRIC,
        FixedKeywordBodies.INDEX,
        FixedKeywordBodies.MATCH,
        FixedKeywordBodies.RANK,
        FixedKeywordBodies.RANK_TYPE,
        FixedKeywordBodies.SORT_LOCALE,
        FixedKeywordBodies.SORT_FUNCTION,
        FixedKeywordBodies.SORT_STRENGTH,
        FixedKeywordBodies.SORTING,
        FixedKeywordBodies.STEMMING,
        FixedKeywordBodies.STRICT,
        FixedKeywordBodies.SUMMARY,
        FixedKeywordBodies.WEIGHTEDSET
    );

	private boolean match(EventCompletionContext context, TokenType keywordToken) {
        return context.document.lexer().matchBackwards(context.position, 1, true, keywordToken, TokenType.COLON) != null
            || context.document.lexer().matchBackwards(context.position, 1, true, keywordToken, TokenType.IDENTIFIER, TokenType.COLON) != null
            || context.document.lexer().matchBackwards(context.position, 1, true, keywordToken, TokenType.IDENTIFIER_WITH_DASH, TokenType.COLON) != null;
	}

	@Override
	public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        if (!(context.document instanceof SchemaDocument)) return List.of();

        for (FixedKeywordBody fixedKeywordBody : colonSupporters) {
            if (match(context, fixedKeywordBody.tokenType())) {
                // Snippets usually not wanted in colon constructs
                return fixedKeywordBody.completionItems().stream()
                                                         .filter(item -> item.getKind() != CompletionItemKind.Snippet)
                                                         .toList();
            }
        }

        return List.of();
	}
}
