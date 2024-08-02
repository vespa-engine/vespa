package ai.vespa.schemals.lsp.completion.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.lsp.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.schemadocument.SchemaDocumentLexer;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;

public class TypeCompletion implements CompletionProvider {

    private TokenType[] compoundTypes = {
        TokenType.MAP,
        TokenType.ARRAY,
        TokenType.WEIGHTEDSET
    };

    private boolean match(EventPositionContext context) {
        SchemaDocumentLexer lexer = context.document.lexer();
        SchemaNode match = lexer.matchBackwards(context.position, 1, false, TokenType.FIELD, TokenType.IDENTIFIER, TokenType.TYPE);
        if (match != null && match.getRange().getStart().getLine() == context.position.getLine()) {
            return true;
        }
        match = lexer.matchBackwards(context.position, 1, false, TokenType.FIELD, TokenType.IDENTIFIER, TokenType.TYPE, TokenType.IDENTIFIER);
        if (match != null && match.getRange().getStart().getLine() == context.position.getLine()) {
            return true;
        }

        for (TokenType tokenType : compoundTypes) {
            match = lexer.matchBackwards(context.position, 3, true, tokenType, TokenType.LESSTHAN);
            if (match != null && match.getRange().getStart().getLine() == context.position.getLine())return true;

            // Dirty gt symbols are inserted when incomplete type is written
            match = lexer.tokenAtOrBeforePosition(context.position, false);
            if (match != null && match.getDirtyType() == TokenType.GREATERTHAN && match.getIsDirty())return true;
        }


        return false;
    }

    private boolean isInsideTensor(EventPositionContext context) {
        SchemaNode lastClean = CSTUtils.getLastCleanNode(context.document.getRootNode(), context.position);

        if (!lastClean.getText().startsWith("tensor")) return false;

        String content = context.document.getCurrentContent();
        int offset = StringUtils.positionToOffset(content, context.position) - 1;
        context.logger.println("Char: " + content.charAt(offset));
        for (int i = offset; i >= offset - 20; --i) {
            if (content.charAt(i) == '>') return false;
            if (content.charAt(i) == '<') return true;
        }
        return false;
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventPositionContext context) {
        if (!(context.document instanceof SchemaDocument)) return List.of();
        if (!match(context)) return List.of();

        if (isInsideTensor(context)) {
            return List.of(
                CompletionUtils.constructType("float"),
                CompletionUtils.constructType("double"),
                CompletionUtils.constructType("int8"),
                CompletionUtils.constructType("bfloat16")
            );
        }

        List<CompletionItem> items = new ArrayList<>() {{
            add(CompletionUtils.constructSnippet("annotationreference", "annotationreference<$1>"));
            add(CompletionUtils.constructSnippet("array", "array<$1>"));
            add(CompletionUtils.constructType("bool"));
            add(CompletionUtils.constructType("byte"));
            add(CompletionUtils.constructType("double"));
            add(CompletionUtils.constructType("float"));
            add(CompletionUtils.constructType("int"));
            add(CompletionUtils.constructType("long"));
            add(CompletionUtils.constructSnippet("map", "map<${1:Kt}, ${2:Vt}>"));
            add(CompletionUtils.constructType("position"));
            add(CompletionUtils.constructType("predicate"));
            add(CompletionUtils.constructType("raw"));
            add(CompletionUtils.constructSnippet("reference", "reference<$1>"));
            add(CompletionUtils.constructType("string"));
            add(CompletionUtils.constructSnippet("tensor", "tensor<$1>($0)"));
            add(CompletionUtils.constructType("uri"));
            add(CompletionUtils.constructSnippet("weightedset", "weightedset<$1>"));
        }};

        Optional<Symbol> documentScope = context.schemaIndex.findSymbol(null, SymbolType.DOCUMENT, ((SchemaDocument)context.document).getSchemaIdentifier());

        if (documentScope.isPresent()) {
            List<Symbol> structSymbols = context.schemaIndex.listSymbolsInScope(documentScope.get(), SymbolType.STRUCT);
            for (var symbol : structSymbols) {
                items.add(CompletionUtils.constructType(symbol.getShortIdentifier()));
            }
        }

        return items;
    }
}
