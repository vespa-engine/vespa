package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.yahoo.schema.Schema;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.Token;

import ai.vespa.schemals.tree.TypeNode;

public class SchemaIndex {

    private PrintStream logger;
    
    private HashMap<String, SchemaIndexItem> database = new HashMap<String, SchemaIndexItem>();

    public class SchemaIndexItem {
        String fileURI;
        Symbol symbol;
        
        public SchemaIndexItem(String fileURI, Symbol symbol) {
            this.fileURI = fileURI;
            this.symbol = symbol;
        }
    }
    
    public SchemaIndex(PrintStream logger) {
        this.logger = logger;
    }
    
    public void clearDocument(String fileURI) {

        Iterator<Map.Entry<String, SchemaIndexItem>> iterator = database.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, SchemaIndexItem> entry = iterator.next();
            if (entry.getValue().fileURI.equals(fileURI)) {
                iterator.remove();
            }
        }
    }

    private String createDBKey(String fileURI, Symbol symbol) {
        return createDBKey(fileURI, symbol.getType(), symbol.getShortIdentifier());
    }

    private String createDBKey(String fileURI, Token.TokenType type, String identifier) {
        return fileURI + ":" + type.name() + ":" + identifier;
    }

    public void insert(String fileURI, Symbol symbol) {
        database.put(
            createDBKey(fileURI, symbol),
            new SchemaIndexItem(fileURI, symbol)
        );
    }

    public Symbol findSymbol(String fileURI, Token.TokenType type, String identifier) {
        SchemaIndexItem results = database.get(createDBKey(fileURI, type, identifier));
        if (results == null) {
            return null;
        }
        return results.symbol;
    }

    public List<Symbol> findSymbolsWithType(String fileURI, TokenType type) {
        List<Symbol> result = new ArrayList<>();
        for (var entry : database.entrySet()) {
            SchemaIndexItem item = entry.getValue();
            if (!item.fileURI.equals(fileURI)) continue;
            if (item.symbol.getType() != type) continue;
            result.add(item.symbol);
        }

        return result;
    }

    public boolean resolveTypeNode(TypeNode typeNode) {
        // TODO: handle inheritance 
        // TODO: handle document types
        // TODO: handle name collision
        String typeName = typeNode.getParsedType().name();
        int matched = 0;
        for (Map.Entry<String, SchemaIndexItem> entry : database.entrySet()) {
            Symbol symbol = entry.getValue().symbol;
            if (symbol.getType() != TokenType.STRUCT) continue;
            if (symbol.getShortIdentifier().equalsIgnoreCase(typeName)) {
                matched += 1;
            }
        }

        return matched == 1;
    }

    public void dumpIndex(PrintStream logger) {
        for (Map.Entry<String, SchemaIndexItem> entry : database.entrySet()) {
            logger.println(entry.getKey() + " -> " + entry.getValue().toString());
        }
    }
}
