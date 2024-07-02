package ai.vespa.schemals.completion.provider;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;


import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.Token;

public class TypeCompletionProvider implements CompletionProvider {
    @Override
    public boolean match(EventPositionContext context) {
        var lexer = context.document.lexer;

        return lexer.matchBackwards(context.position, 1, false, Token.TokenType.FIELD, Token.TokenType.IDENTIFIER, Token.TokenType.TYPE) != null;
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
            CompletionUtils.constructSnippet("tensor", "tensor<$1>($0)"),
            CompletionUtils.constructType("uri"),
            CompletionUtils.constructSnippet("weightedset", "weightedset<$1>")
        });
    }
}
