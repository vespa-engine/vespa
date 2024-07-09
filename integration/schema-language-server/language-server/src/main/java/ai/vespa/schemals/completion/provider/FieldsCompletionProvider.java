package ai.vespa.schemals.completion.provider;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.completion.utils.CompletionUtils;
import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.context.SchemaDocumentLexer.LexicalToken;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * FieldsCompletionProvider
 */
public class FieldsCompletionProvider implements CompletionProvider {

	@Override
	public boolean match(EventPositionContext context) {
        Position searchPos = context.startOfWord();
        if (searchPos == null)searchPos = context.position;
        SchemaNode last = CSTUtils.getLastCleanNode(context.document.getRootNode(), searchPos);

        // Skip previously declared fields in the list
        // to get to the start where we can determine the pattern fields: ...
        while (last != null && 
                last.getType() == TokenType.IDENTIFIER && 
                (last.getPrevious() != null && last.getPrevious().getType() == TokenType.COMMA)) {
            last = last.getPrevious().getPrevious();
        }

        if (last == null)return false;

        searchPos = last.getRange().getStart();

        LexicalToken match = context.document.lexer.matchBackwards(searchPos, 0, false, TokenType.FIELDS, TokenType.COLON, TokenType.IDENTIFIER);

        // Has to be on the same line
        if (match != null && match.range().getStart().getLine() == context.position.getLine())return true;
        return (last != null && last.getType() == TokenType.FIELDS && last.getRange().getStart().getLine() == context.position.getLine());
	}

	@Override
	public List<CompletionItem> getCompletionItems(EventPositionContext context) {
        List<Symbol> fieldSymbols = context.schemaIndex.findSymbolsWithTypeInDocument(context.document.getFileURI(), TokenType.FIELD);

        return fieldSymbols.stream()
                           .map(symbol -> CompletionUtils.constructBasic(symbol.getShortIdentifier()))
                           .collect(Collectors.toList());
	}
}
