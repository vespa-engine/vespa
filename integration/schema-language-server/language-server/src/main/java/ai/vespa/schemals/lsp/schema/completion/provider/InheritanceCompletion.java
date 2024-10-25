package ai.vespa.schemals.lsp.schema.completion.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.schema.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.COMMA;
import ai.vespa.schemals.parser.ast.IDENTIFIER;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * For completing user defined identifiers after "inherits".
 */
public class InheritanceCompletion implements CompletionProvider {
    private Optional<SchemaNode> match(EventCompletionContext context) {
        Position searchPos = context.startOfWord();
        if (searchPos == null)searchPos = context.position;
        SchemaNode last = CSTUtils.getLastCleanNode(context.document.getRootNode(), searchPos);
        if (last == null) return Optional.empty();

        if (last.isASTInstance(IDENTIFIER.class))last = last.getParent();
        if (last.isASTInstance(COMMA.class))last = last.getPrevious();


        // Skip previously declared fields in the list
        // to get to the start where we can determine the pattern fields: ...
        while (last != null && 
                (last.isASTInstance(identifierStr.class) || last.isASTInstance(identifierWithDashStr.class)) && 
                (last.getPrevious() != null && last.getPrevious().getSchemaType() == TokenType.COMMA)) {
        last = last.getPrevious().getPrevious();
        }

        if (last == null)return Optional.empty();

        searchPos = last.getRange().getStart();

        SchemaNode match = context.document.lexer().matchBackwards(searchPos, 1, false, TokenType.IDENTIFIER, TokenType.INHERITS);

        // edge case for rank profiles.
        if (match != null && match.getParent() != null && match.getParent().getParent() != null && match.getParent().getParent().isASTInstance(identifierWithDashStr.class))
            match = match.getParent();

        // Has to be on the same line
        if (match != null && match.getRange().getStart().getLine() == context.position.getLine()
                && match.getParent().hasSymbol()) return Optional.of(match);

        return Optional.empty();
    }

	@Override
	public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        Optional<SchemaNode> matched = match(context);
        if (matched.isEmpty()) return List.of();

        Symbol identifierSymbol = matched.get().getParent().getSymbol();

        List<CompletionItem> ret = new ArrayList<>();

        boolean defaultSeen = false;
        Symbol scope = (identifierSymbol.getType() == SymbolType.DOCUMENT 
                     || identifierSymbol.getType() == SymbolType.SCHEMA 
                     || identifierSymbol.getType() == SymbolType.RANK_PROFILE) 
                      ? null : identifierSymbol.getScope();

        for (var symbol : context.schemaIndex.listSymbolsInScope(scope, identifierSymbol.getType())) {
            if (symbol.getShortIdentifier().equals(identifierSymbol.getShortIdentifier()))continue; // identifier-based, since symbols may be inaccurate in this context
            ret.add(CompletionUtils.constructBasic(symbol.getShortIdentifier(), symbol.getLongIdentifier()));
            if (symbol.getShortIdentifier().equals("default"))defaultSeen = true;
        }

        if (identifierSymbol.getType() == SymbolType.RANK_PROFILE && !defaultSeen)ret.add(CompletionUtils.constructBasic("default"));

        return ret;
	}

    
}
