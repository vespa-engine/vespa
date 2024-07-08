package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.yahoo.schema.Schema;

import ai.vespa.schemals.context.SchemaDocumentParser;

import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.AnnotationReferenceNode;
import ai.vespa.schemals.tree.TypeNode;

public class SchemaIndex {

    private PrintStream logger;
    
    private HashMap<String, SchemaIndexItem> database = new HashMap<String, SchemaIndexItem>();

    // Map fileURI -> SchemaDocumentParser
    private HashMap<String, SchemaDocumentParser> openSchemas = new HashMap<String, SchemaDocumentParser>();

    private DocumentInheritanceGraph documentInheritanceGraph;

    // Schema inheritance. Can only inherit one schema
    private HashMap<String, String> schemaInherits = new HashMap<String, String>();

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
        this.documentInheritanceGraph = new DocumentInheritanceGraph();
    }
    
    public void clearDocument(String fileURI) {

        Iterator<Map.Entry<String, SchemaIndexItem>> iterator = database.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, SchemaIndexItem> entry = iterator.next();
            if (entry.getValue().fileURI.equals(fileURI)) {
                iterator.remove();
            }
        }

        openSchemas.remove(fileURI);
        documentInheritanceGraph.clearInheritsList(fileURI);
    }

    private String createDBKey(String fileURI, Symbol symbol) {
        return createDBKey(fileURI, symbol.getType(), symbol.getShortIdentifier());
    }

    private String createDBKey(String fileURI, TokenType type, String identifier) {
        return fileURI + ":" + type.name() + ":" + identifier.toLowerCase(); // identifiers in SD are not sensitive to casing
    }

    public void registerSchema(String fileURI, SchemaDocumentParser schemaDocumentParser) {
        openSchemas.put(fileURI, schemaDocumentParser);
    }

    public void insert(String fileURI, Symbol symbol) {
        database.put(
            createDBKey(fileURI, symbol),
            new SchemaIndexItem(fileURI, symbol)
        );
    }

    public Symbol findSymbol(String fileURI, TokenType type, String identifier) {
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

    public boolean resolveTypeNode(TypeNode typeNode, String fileURI) {
        // TODO: handle document reference
        // TODO: handle name collision (diamond etc.)
        String typeName = typeNode.getParsedType().name().toLowerCase();

        List<String> inheritanceList = documentInheritanceGraph.getAllDocumentAncestorURIs(fileURI);

        for (var parentURI : inheritanceList) {
            if (findSymbol(parentURI, TokenType.STRUCT, typeName.toLowerCase()) != null) return true;
        }

        return false;
    }

    public boolean resolveAnnotationReferenceNode(AnnotationReferenceNode annotationReferenceNode, String fileURI) {
        // TODO: inheritance
        String annotationName = annotationReferenceNode.getText().toLowerCase();

        return findSymbol(fileURI, TokenType.ANNOTATION, annotationName.toLowerCase()) != null;
    }

    public void dumpIndex(PrintStream logger) {
        logger.println(" === INDEX === ");
        for (Map.Entry<String, SchemaIndexItem> entry : database.entrySet()) {
            logger.println(entry.getKey() + " -> " + entry.getValue().toString());
        }

        logger.println(" === INHERITANCE === ");
        documentInheritanceGraph.dumpAllEdges(logger);
    }

    public Symbol findSchemaIdentifierSymbol(String fileURI) {
        // TODO: handle duplicates?
        TokenType[] schemaIdentifierTypes = new TokenType[] {
            TokenType.SCHEMA,
            TokenType.SEARCH,
            TokenType.DOCUMENT
        };

        for (TokenType tokenType : schemaIdentifierTypes) {
            var result = findSymbolsWithType(fileURI, tokenType);

            if (result.isEmpty()) continue;
            return result.get(0);
        }

        return null;
    }

    public SchemaDocumentParser findSchemaDocumentWithName(String name) {
        for (Map.Entry<String, SchemaDocumentParser> entry : openSchemas.entrySet()) {
            SchemaDocumentParser schemaDocumentParser = entry.getValue();
            String schemaIdentifier = schemaDocumentParser.getSchemaIdentifier();
            if (schemaIdentifier != null && schemaIdentifier.equalsIgnoreCase(name)) {
                return schemaDocumentParser;
            }
        }
        return null;
    }

    public boolean tryRegisterDocumentInheritance(String childURI, String parentURI) {
        return this.documentInheritanceGraph.addInherits(childURI, parentURI);
    }

    public List<String> getAllDocumentDescendants(String fileURI) {
        List<String> descendants = this.documentInheritanceGraph.getAllDocumentDescendantURIs(fileURI);
        descendants.remove(fileURI);
        return descendants;
    }

    public void setSchemaInherits(String childURI, String parentURI) {
        schemaInherits.put(childURI, parentURI);
    }
}
