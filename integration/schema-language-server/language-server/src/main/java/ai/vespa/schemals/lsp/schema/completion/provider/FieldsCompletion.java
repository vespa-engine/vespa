package ai.vespa.schemals.lsp.schema.completion.provider;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.lsp.schema.completion.utils.CompletionUtils;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.schemadocument.SchemaDocument;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * FieldsCompletionProvider
 * For completing user defined fields in a fieldset.
 */
public class FieldsCompletion implements CompletionProvider {

	private boolean match(EventCompletionContext context) {
        SchemaNode match = context.document.lexer().matchBackwardsOnLine(context.position, false, TokenType.FIELDS, TokenType.COLON);

        if (match != null && match.getRange().getStart().getLine() == context.position.getLine())return true;
        return false;
    }

    private int previousBeforeDot(String documentContent, Position position) {
        int originalOffset = StringUtils.positionToOffset(documentContent, position);
        for (int offset = originalOffset - 1; offset >= 0; --offset) {
            if (Character.isWhitespace(documentContent.charAt(offset))) return -1;
            if (documentContent.charAt(offset) == '.') return originalOffset - offset + 1;
        }
        return -1;
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        if (!(context.document instanceof SchemaDocument)) return List.of();
        if (!match(context)) return List.of();

        int beforeDotDelta = previousBeforeDot(context.document.getCurrentContent(), context.position);

        if (beforeDotDelta != -1) {
            Node prevSymbol = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), new Position(context.position.getLine(), context.position.getCharacter() - beforeDotDelta));
            if (prevSymbol == null) return List.of();

            Optional<Symbol> prevSymbolDefinition = context.schemaIndex.getSymbolDefinition(prevSymbol.getSymbol());

            if (prevSymbolDefinition.isEmpty()) return List.of();

            List<Symbol> fieldSymbols = context.schemaIndex.listSymbolsInScope(prevSymbolDefinition.get(), EnumSet.of(SymbolType.FIELD, SymbolType.MAP_KEY, SymbolType.MAP_VALUE));

            Optional<Symbol> structDefinition = context.schemaIndex.fieldIndex().findFieldStructDefinition(prevSymbolDefinition.get());

            if (structDefinition.isPresent()) {
                fieldSymbols.addAll(context.schemaIndex.listSymbolsInScope(structDefinition.get(), SymbolType.FIELD));
            }

            List<CompletionItem> ret = new ArrayList<>();
            Set<String> addedIdentifiers = new HashSet<>();

            for (Symbol symbol : fieldSymbols) {
                if (addedIdentifiers.contains(symbol.getShortIdentifier()))continue;
                addedIdentifiers.add(symbol.getShortIdentifier());

                ret.add(CompletionUtils.constructBasic(symbol.getShortIdentifier(), symbol.getLongIdentifier()));
            }
            return ret;
        } else {
            Optional<Symbol> scope = context.schemaIndex.findSymbol(null, SymbolType.DOCUMENT, ((SchemaDocument)context.document).getSchemaIdentifier());

            if (scope.isEmpty()) return List.of();

            List<Symbol> fieldSymbols = context.schemaIndex.listSymbolsInScope(scope.get(), SymbolType.FIELD);

            return fieldSymbols.stream()
                               .map(symbol -> CompletionUtils.constructBasic(symbol.getPrettyIdentifier()))
                               .toList();
        }
	}
}
