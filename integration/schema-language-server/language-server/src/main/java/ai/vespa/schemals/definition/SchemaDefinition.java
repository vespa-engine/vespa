package ai.vespa.schemals.definition;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Location;

import ai.vespa.schemals.context.EventPositionContext;
import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.parser.ast.fieldsElm;

public class SchemaDefinition {

    private static HashMap<Class<? extends Node>, SymbolType> linkContexts = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(fieldsElm.class, SymbolType.FIELD);
    }};

    public static ArrayList<Location> getDefinition(
        EventPositionContext context
    ) {

        ArrayList<Location> ret = new ArrayList<Location>();

        SchemaNode node = context.document.getSymbolAtPosition(context.position);

        if (node == null || !node.hasSymbol()) {
            return ret;
        }

        Symbol search = node.getSymbol();

        Symbol result = context.schemaIndex.findSymbol(context.document.getFileURI(), search.getType(), search.getLongIdentifier());

        if (result == null && search.getType() == SymbolType.DOCUMENT) {
            result = context.schemaIndex.findSymbol(context.document.getFileURI(), SymbolType.SCHEMA, search.getLongIdentifier()); 
        }

        if (result == null) {
            return ret;
        }

        ret.add(new Location(result.getFileURI(), result.getNode().getRange()));
        return ret;
    }
}
