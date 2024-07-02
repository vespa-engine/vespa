package ai.vespa.schemals.completion.provider;

import java.util.List;
import java.util.ArrayList;

import org.eclipse.lsp4j.CompletionItem;


import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.completion.provider.CompletionProvider;

import ai.vespa.schemals.parser.Token;

public class TypeCompletionProvider implements CompletionProvider {
    @Override
    public boolean match(EventPositionContext context) {
        //var lexer = context.document.lexer;

        //return lexer.matchBackwards(context.position, 1, Token.TokenType.FIELD, Token.TokenType.IDENTIFIER, Token.TokenType.TYPE);
        return false;
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventPositionContext context) {
        return new ArrayList<>();
    }
}
