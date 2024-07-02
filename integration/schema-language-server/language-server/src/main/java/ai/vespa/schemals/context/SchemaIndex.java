package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.SymbolInformation;

import ai.vespa.schemals.parser.*;
import ai.vespa.schemals.parser.Token.TokenType;

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

}
