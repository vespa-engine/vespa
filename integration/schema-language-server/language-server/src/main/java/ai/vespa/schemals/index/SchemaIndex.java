package ai.vespa.schemals.index;

import static ai.vespa.schemals.parser.Token.TokenType.DOCUMENT;
import static ai.vespa.schemals.parser.Token.TokenType.SCHEMA;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ai.vespa.schemals.context.SchemaDocumentParser;

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
        return fileURI + ":" + type.name().toLowerCase() + ":" + identifier.toLowerCase(); // identifiers in SD are not sensitive to casing
    }

    public void registerSchema(String fileURI, SchemaDocumentParser schemaDocumentParser) {
        openSchemas.put(fileURI, schemaDocumentParser);
        documentInheritanceGraph.createNodeIfNotExists(fileURI);
    }

    public void insert(String fileURI, Symbol symbol) {
        database.put(
            createDBKey(fileURI, symbol),
            new SchemaIndexItem(fileURI, symbol)
        );
    }

    /*
    * Searches for the given symbol ONLY in document pointed to by fileURI
    */
    public Symbol findSymbolInFile(String fileURI, TokenType type, String identifier) {
        SchemaIndexItem results = database.get(createDBKey(fileURI, type, identifier));

        if (results == null) {
            // Edge case for when a document is defined without explicitly stating the name.
            if (type == DOCUMENT) {
                return findSymbolInFile(fileURI, SCHEMA, identifier);
            }
            return null;
        }
        return results.symbol;
    }

    /*
     * Search for a user defined identifier symbol. Also search in inherited documents.
     */
    public Symbol findSymbol(String fileURI, TokenType type, String identifier) {
        // TODO: handle name collision (diamond etc.)
        List<String> inheritanceList = documentInheritanceGraph.getAllDocumentAncestorURIs(fileURI);

        for (var parentURI : inheritanceList) {
            Symbol result = findSymbolInFile(parentURI, type, identifier);
            if (result != null) return result;
        }
        return null;
    }

    public List<Symbol> findSymbolsWithTypeInDocument(String fileURI, TokenType type) {
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
        String typeName = typeNode.getParsedType().name().toLowerCase();

        // Probably struct
        if (findSymbol(fileURI, TokenType.STRUCT, typeName.toLowerCase()) != null) return true;

        // If its not struct it could be document. The document can be defined anywhere
        for (String documentURI : openSchemas.keySet()) {
            if (findSymbol(documentURI, TokenType.DOCUMENT, typeName.toLowerCase()) != null) return true;
        }

        return false;
    }

    public boolean resolveAnnotationReferenceNode(AnnotationReferenceNode annotationReferenceNode, String fileURI) {
        String annotationName = annotationReferenceNode.getText().toLowerCase();

        return findSymbol(fileURI, TokenType.ANNOTATION, annotationName) != null;
    }

    public void dumpIndex(PrintStream logger) {
        logger.println(" === INDEX === ");
        for (Map.Entry<String, SchemaIndexItem> entry : database.entrySet()) {
            logger.println(entry.getKey() + " -> " + entry.getValue().toString());
        }

        logger.println(" === INHERITANCE === ");
        documentInheritanceGraph.dumpAllEdges(logger);

        logger.println(" === TRACKED FILES === ");
        for (Map.Entry<String, SchemaDocumentParser> entry : openSchemas.entrySet()) {
            SchemaDocumentParser document = entry.getValue();
            logger.println(document.toString());
        }
    }

    public Symbol findSchemaIdentifierSymbol(String fileURI) {
        // TODO: handle duplicates?
        TokenType[] schemaIdentifierTypes = new TokenType[] {
            TokenType.SCHEMA,
            TokenType.SEARCH,
            TokenType.DOCUMENT
        };

        for (TokenType tokenType : schemaIdentifierTypes) {
            var result = findSymbolsWithTypeInDocument(fileURI, tokenType);

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

    public SchemaDocumentParser findSchemaDocumentWithFileURI(String fileURI) {
        return openSchemas.get(fileURI);
    }

    public boolean tryRegisterDocumentInheritance(String childURI, String parentURI) {
        return this.documentInheritanceGraph.addInherits(childURI, parentURI);
    }

    public List<String> getAllDocumentAncestorURIs(String fileURI) {
        return this.documentInheritanceGraph.getAllDocumentAncestorURIs(fileURI);
    }

    public List<String> getAllDocumentDescendants(String fileURI) {
        List<String> descendants = this.documentInheritanceGraph.getAllDocumentDescendantURIs(fileURI);
        descendants.remove(fileURI);
        return descendants;
    }

    public List<String> getAllDocumentsInInheritanceOrder() {
        return this.documentInheritanceGraph.getAllDocumentsTopoOrder();
    }

    public void setSchemaInherits(String childURI, String parentURI) {
        schemaInherits.put(childURI, parentURI);
    }

    public int numEntries() {
        return this.database.size();
    }
}
