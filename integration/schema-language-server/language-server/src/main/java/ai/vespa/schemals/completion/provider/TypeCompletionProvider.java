package ai.vespa.schemals.completion.provider;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;


import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.SchemaDocumentLexer;
import ai.vespa.schemals.context.SchemaDocumentLexer.LexicalToken;
import ai.vespa.schemals.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.parser.Token.TokenType;

public class TypeCompletionProvider implements CompletionProvider {

    private TokenType[] compoundTypes = {
        TokenType.MAP,
        TokenType.ARRAY,
        TokenType.WEIGHTEDSET
    };

    @Override
    public boolean match(EventPositionContext context) {
        SchemaDocumentLexer lexer = context.document.lexer;
        LexicalToken match = lexer.matchBackwards(context.position, 1, false, TokenType.FIELD, TokenType.IDENTIFIER, TokenType.TYPE);
        if (match != null && match.range().getStart().getLine() == context.position.getLine()) {
            return true;
        }
        match = lexer.matchBackwards(context.position, 1, false, TokenType.FIELD, TokenType.IDENTIFIER, TokenType.TYPE, TokenType.IDENTIFIER);
        if (match != null && match.range().getStart().getLine() == context.position.getLine()) {
            return true;
        }

        for (TokenType tokenType : compoundTypes) {
            match = lexer.matchBackwards(context.position, 3, true, tokenType, TokenType.LESSTHAN);
            if (match != null && match.range().getStart().getLine() == context.position.getLine())return true;

            // Dirty gt symbols are inserted when incomplete type is written
            match = lexer.tokenAtOrBeforePosition(context.position, false);
            if (match != null && match.type() == TokenType.GREATERTHAN && match.isDirty())return true;
        }


        return false;
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventPositionContext context) {
        return List.of(new CompletionItem[] {
            CompletionUtils.constructSnippet("annotationreference", "annotationreference<$1>"),
            CompletionUtils.constructSnippet("array", "array<$1>"),
            CompletionUtils.constructType("bool"),
            CompletionUtils.constructType("byte"),
            CompletionUtils.constructType("double"),
            CompletionUtils.constructType("float"),
            CompletionUtils.constructType("int"),
            CompletionUtils.constructType("long"),
            CompletionUtils.constructSnippet("map", "map<${1:Kt}, ${2:Vt}>"),
            CompletionUtils.constructType("position"),
            CompletionUtils.constructType("predicate"),
            CompletionUtils.constructType("raw"),
            CompletionUtils.constructSnippet("reference", "reference<$1>"),
            //CompletionUtils.constructType("struct"), TODO: find defined structs
            CompletionUtils.constructType("string"),
            CompletionUtils.constructSnippet("tensor", "tensor<$1>($0)"),
            CompletionUtils.constructType("uri"),
            CompletionUtils.constructSnippet("weightedset", "weightedset<$1>")
        });
    }
}
