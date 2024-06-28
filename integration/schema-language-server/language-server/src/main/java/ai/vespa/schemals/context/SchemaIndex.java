package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import ai.vespa.schemals.tree.SchemaNode;

import ai.vespa.schemals.parser.*;

public class SchemaIndex {

    private PrintStream logger;
    
    private HashMap<String, SchemaIndexItem> database = new HashMap<String, SchemaIndexItem>();

    public class SchemaIndexItem {
        Token.TokenType type;
        String fileURI;
        SchemaNode node;
        
        public SchemaIndexItem(Token.TokenType type, String fileURI, SchemaNode node) {
            this.type = type;
            this.fileURI = fileURI;
            this.node = node;
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

    private String createDBKey(String fileURI, Token.TokenType type, String identifier) {
        return fileURI + ":" + type.name() + ":" + identifier;
    }

    public void insert(String fileURI, Token.TokenType type, String identifier, SchemaNode node) {
        database.put(
            createDBKey(fileURI, type, identifier),
            new SchemaIndexItem(type, fileURI, node)
        );
    }

    public SchemaNode findSymbol(String fileURI, Token.TokenType type, String identifier) {
        SchemaIndexItem results = database.get(createDBKey(fileURI, type, identifier));
        if (results == null) {
            return null;
        }
        return results.node;
    }

    public ArrayList<SchemaNode> findSymbols(String fileURI, Token.TokenType type) {
        ArrayList<SchemaNode> ret = new ArrayList<SchemaNode>(); 

        for (Map.Entry<String, SchemaIndexItem> set : database.entrySet()) {
            SchemaIndexItem item = set.getValue();
            if (item.fileURI == fileURI && item.type == type) {
                ret.add(item.node);
            }
        }

        return ret;
    }
}
