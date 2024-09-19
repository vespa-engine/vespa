package ai.vespa.schemals.lsp.schema.definition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Location;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Responsible for LSP textDocument/definition requests.
 */
public class SchemaDefinition {
    public static List<Location> getDefinition(EventPositionContext context) {
        ArrayList<Location> ret = new ArrayList<Location>();

        SchemaNode node = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);

        if (node == null || !node.hasSymbol()) {
            return ret;
        }

        Symbol search = node.getSymbol();

        // Try the faster search first
        Optional<Symbol> results = context.schemaIndex.getNextSymbolDefinition(search);

        if (results.isEmpty() && search.getType() == SymbolType.DOCUMENT) {
            results = context.schemaIndex.findSymbol(search); 
        }

        if (results.isPresent()) {
            ret.add(results.get().getLocation());
        } else if (search.getStatus() == SymbolStatus.DEFINITION) {
            ret.add(search.getLocation());
        }

        return ret;
    }
}
