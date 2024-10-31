package ai.vespa.schemals.lsp.schema.references;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Location;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Responsible for LSP textDocument/references requests.
 */
public class SchemaReferences {

    public static List<Location> getReferences(EventPositionContext context) {

        List<Location> ret = new ArrayList<>();

        SchemaNode node = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);

        if (node == null) {
            return ret;
        }

        Symbol originalSymbol = node.getSymbol();

        if (originalSymbol.getStatus() == SymbolStatus.INVALID || originalSymbol.getStatus() == SymbolStatus.UNRESOLVED) {
            context.logger.info("Could not resolve because status is " + originalSymbol.getStatus().toString());
            return ret;
        }

        Symbol search = originalSymbol;
        boolean originalSymbolIsDefinition = originalSymbol.getStatus() == SymbolStatus.DEFINITION;
        if (!originalSymbolIsDefinition) {

            Optional<Symbol> results = context.schemaIndex.getSymbolDefinition(originalSymbol);
            if (results.isEmpty()) {
                context.logger.info("Could not resolve because definition was not found.");
                return ret;
            }
            search = results.get();
        }

        List<Symbol> symbols = context.schemaIndex.getSymbolReferences(search);

        if (!originalSymbolIsDefinition) {
            symbols.add(search);
        }

        context.logger.info("Getsymbolreferences returned " + symbols.size() + " results");
        for (Symbol symbol : symbols) {
            if (symbol != originalSymbol) {
                ret.add(symbol.getLocation());
            }
        }

        return ret;
    }
}
