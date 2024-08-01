package ai.vespa.schemals.schemadocument.resolvers;

import java.util.Optional;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;

public class TypeNodeResolver {
    public static boolean resolveType(ParseContext context, Symbol symbol) {
        // Probably a struct
        Optional<Symbol> definition = context.schemaIndex().findSymbol(symbol.getScope(), SymbolType.STRUCT, symbol.getShortIdentifier());
        if (definition.isPresent()) {

            symbol.setType(SymbolType.STRUCT);
            symbol.setStatus(SymbolStatus.REFERENCE);

            context.schemaIndex().insertSymbolReference(definition.get(), symbol);
            return true;
        }

        // If it's not a struct it could be a document.
        definition = context.schemaIndex().findSymbol(symbol.getScope(), SymbolType.DOCUMENT, symbol.getShortIdentifier());
        if (definition.isPresent()) {

            symbol.setType(SymbolType.DOCUMENT);
            symbol.setStatus(SymbolStatus.REFERENCE);

            context.schemaIndex().insertSymbolReference(definition.get(), symbol);
            return true;
        }

        // Set type to struct so it doesn't try to get handled later
        symbol.setType(SymbolType.STRUCT);

        return false;
    }
}
