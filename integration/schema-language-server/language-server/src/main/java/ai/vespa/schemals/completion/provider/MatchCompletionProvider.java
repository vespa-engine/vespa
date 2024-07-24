package ai.vespa.schemals.completion.provider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.completion.utils.CompletionUtils;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.schemadocument.SchemaDocument;

public class MatchCompletionProvider implements CompletionProvider {

	private boolean match(EventPositionContext context) {
        return context.document.lexer().matchBackwards(context.position, 1, true, TokenType.MATCH, TokenType.COLON) != null;
	}

	@Override
	public List<CompletionItem> getCompletionItems(EventPositionContext context) {
        if (!(context.document instanceof SchemaDocument)) return List.of();
        if (!match(context)) return List.of();
        return List.of(new CompletionItem[]{
            CompletionUtils.constructBasic("text"), 
            CompletionUtils.constructBasic("word"), 
            CompletionUtils.constructBasic("exact"), 
            CompletionUtils.constructBasic("exact"), 
            CompletionUtils.constructBasic("gram"), 
            CompletionUtils.constructBasic("cased"), 
            CompletionUtils.constructBasic("uncased"), 
            CompletionUtils.constructBasic("prefix"), 
            CompletionUtils.constructBasic("substring"), 
            CompletionUtils.constructBasic("suffix"), 
        });
	}
}
