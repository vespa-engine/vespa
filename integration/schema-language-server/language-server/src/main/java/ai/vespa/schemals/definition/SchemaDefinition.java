package ai.vespa.schemals.definition;

import java.util.ArrayList;

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
        Symbol result = context.schemaIndex.findSymbol(context.document.getFileURI(), search.getType(), search.getLongIdentifier());

        if (result == null && search.getType() == SymbolType.DOCUMENT) {
            result = context.schemaIndex.findSymbol(context.document.getFileURI(), SymbolType.SCHEMA, search.getLongIdentifier()); 
        }

        if (result == null) {
            context.schemaIndex.findDefinitionOfReference(search).ifPresent(
                symbol -> ret.add(symbol.getLocation())
            );
        } else {
            ret.add(result.getLocation());
        }

        return ret;
    }
}
