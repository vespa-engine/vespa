package ai.vespa.schemals.completion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.completion.provider.*;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.parser.*;
import ai.vespa.schemals.tree.CSTUtils;

public class SchemaCompletion {

    private static CompletionProvider[] providers = {
        new BodyKeywordCompletionProvider(),
        new TypeCompletionProvider()
    };

    public static ArrayList<CompletionItem> getCompletionItems(EventPositionContext context) {

        var lexer = context.document.lexer;

        //if (lexer.matchBackwards(context.position, 1, Token.TokenType.FIELD, Token.TokenType.IDENTIFIER, Token.TokenType.TYPE)) {
        //    return getTypeCompletion(context);
        //} else {
        //    return getBodyKeywordCompletion(context);
        //}

        var lastToken = context.document.lexer.tokenBeforePosition(context.position, true);
        context.document.lexer.dumpTokens(context.logger);

        context.logger.println(context.toString());
        context.logger.println(lastToken.toString());

        ArrayList<CompletionItem> ret = new ArrayList<CompletionItem>();

        for (CompletionProvider provider : providers) {
            if (provider.match(context)) {
                ret.addAll(provider.getCompletionItems(context));
            }
        }

        return ret;
    }
}
