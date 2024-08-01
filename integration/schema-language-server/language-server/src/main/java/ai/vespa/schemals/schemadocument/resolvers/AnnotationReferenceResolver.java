package ai.vespa.schemals.schemadocument.resolvers;

import java.util.Optional;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;

public class AnnotationReferenceResolver {
    public static boolean resolveAnnotationReference(ParseContext context, Symbol symbol) {

        Optional<Symbol> definition = context.schemaIndex().findSymbol(symbol);
        if (definition.isPresent()) {
            symbol.setStatus(SymbolStatus.REFERENCE);
            context.schemaIndex().insertSymbolReference(definition.get(), symbol);
            return true;
        }
        return false;
    }
}
