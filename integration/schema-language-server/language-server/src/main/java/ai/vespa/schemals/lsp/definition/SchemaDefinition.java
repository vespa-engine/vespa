package ai.vespa.schemals.lsp.definition;

import java.util.ArrayList;
import java.util.Optional;

import org.eclipse.lsp4j.Location;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class SchemaDefinition {
    public static ArrayList<Location> getDefinition(EventPositionContext context) {

        ArrayList<Location> ret = new ArrayList<Location>();

        SchemaNode node = CSTUtils.getSymbolAtPosition(context.document.getRootNode(), context.position);

        if (node == null || !node.hasSymbol()) {
            return ret;
        }

        Symbol search = node.getSymbol();

        // Try the faster search first
        Optional<Symbol> results = context.schemaIndex.getSymbolDefinition(search);

        if (results.isEmpty() && search.getType() == SymbolType.DOCUMENT) {
            results = context.schemaIndex.findSymbol(search); 
        }

        // TODO: refactor
        // if (result == null) {
        //     context.schemaIndex.findDefinitionOfReference(search).ifPresent(
        //         symbol -> ret.add(symbol.getLocation())
        //     );
        // } else {
        //     ret.add(result.getLocation());
        // }

        if (results.isPresent()) {
            ret.add(results.get().getLocation());
        }

        return ret;
    }
}
