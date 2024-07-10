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
import ai.vespa.schemals.tree.SymbolDefinitionNode;
import ai.vespa.schemals.tree.SymbolNode;
import ai.vespa.schemals.tree.SymbolReferenceNode;
import ai.vespa.schemals.tree.TypeNode;

public class SchemaIndex {

    private PrintStream logger;
    
    private HashMap<String, Symbol> symbolDefinitionsDB = new HashMap<String, Symbol>();
    private HashMap<String, ArrayList<Symbol>> symbolReferencesDB = new HashMap<>();

    // Map fileURI -> SchemaDocumentParser
    private HashMap<String, SchemaDocumentParser> openSchemas = new HashMap<String, SchemaDocumentParser>();

    private DocumentInheritanceGraph documentInheritanceGraph;

    // Schema inheritance. Can only inherit one schema
    private HashMap<String, String> schemaInherits = new HashMap<String, String>();
    
    public SchemaIndex(PrintStream logger) {
        this.logger = logger;
        this.documentInheritanceGraph = new DocumentInheritanceGraph();
    }
    
    public void clearDocument(String fileURI) {

        Iterator<Map.Entry<String, Symbol>> iterator = symbolDefinitionsDB.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Symbol> entry = iterator.next();
            if (entry.getValue().getFileURI().equals(fileURI)) {
                iterator.remove();
            }
        }

        Iterator<Map.Entry<String, ArrayList<Symbol>>> refIterator = symbolReferencesDB.entrySet().iterator();

        while (refIterator.hasNext()) {
            Map.Entry<String, ArrayList<Symbol>> entry = refIterator.next();
            if (entry.getKey().startsWith(fileURI)) {
                refIterator.remove();
            }
        }

        openSchemas.remove(fileURI);
        documentInheritanceGraph.clearInheritsList(fileURI);
    }

    private String createDBKey(Symbol symbol) {
        return createDBKey(symbol.getFileURI(), symbol.getType(), symbol.getShortIdentifier());
    }

    private String createDBKey(String fileURI, TokenType type, String identifier) {
        // Some logic in this file depends on that the key beginsWith the fileURI
        return fileURI + ":" + type.name().toLowerCase() + ":" + identifier.toLowerCase(); // identifiers in SD are not sensitive to casing
    }

    public void registerSchema(String fileURI, SchemaDocumentParser schemaDocumentParser) {
        openSchemas.put(fileURI, schemaDocumentParser);
        documentInheritanceGraph.createNodeIfNotExists(fileURI);
    }

    public void insertSymbol(String fileURI, SymbolDefinitionNode node) {
        Symbol symbol = new Symbol(node, fileURI);
        symbolDefinitionsDB.put(
            createDBKey(symbol),
            symbol
        );
    }

    /*
    * Searches for the given symbol ONLY in document pointed to by fileURI
    */
    public Symbol findSymbolInFile(String fileURI, TokenType type, String identifier) {
        Symbol results = symbolDefinitionsDB.get(createDBKey(fileURI, type, identifier));

        if (results == null) {
            // Edge case for when a document is defined without explicitly stating the name.
            if (type == DOCUMENT) {
                return findSymbolInFile(fileURI, SCHEMA, identifier);
            }
            return null;
        }
        return results;
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

    /**
     * Finds symbol references in the schema index for the given symbol.
     *
     * @param symbol The symbol to find references for.
     * @return A list of symbol references found in the schema index.
     */
    public ArrayList<Symbol> findSymbolReferences(Symbol symbol) {
        ArrayList<Symbol> results = symbolReferencesDB.get(createDBKey(symbol));

        if (results == null) {
            results = new ArrayList<>();
        }

        return results;
    }

    public List<Symbol> findSymbolsWithTypeInDocument(String fileURI, TokenType type) {
        List<Symbol> result = new ArrayList<>();
        for (var entry : symbolDefinitionsDB.entrySet()) {
            Symbol item = entry.getValue();
            if (!item.getFileURI().equals(fileURI)) continue;
            if (item.getType() != type) continue;
            result.add(item);
        }

        return result;
    }

    public boolean resolveTypeNode(TypeNode typeNode, String fileURI) {
        // TODO: handle document reference
        String typeName = typeNode.getParsedType().name().toLowerCase();

        // Probably struct
        if (findSymbol(fileURI, TokenType.STRUCT, typeName.toLowerCase()) != null) return true;
        if (findSymbol(fileURI, TokenType.DOCUMENT, typeName.toLowerCase()) != null) return true;
        return false;
    }

    public boolean resolveAnnotationReferenceNode(AnnotationReferenceNode annotationReferenceNode, String fileURI) {
        String annotationName = annotationReferenceNode.getText().toLowerCase();

        return findSymbol(fileURI, TokenType.ANNOTATION, annotationName) != null;
    }

    public void dumpIndex(PrintStream logger) {
        logger.println(" === INDEX === ");
        for (Map.Entry<String, Symbol> entry : symbolDefinitionsDB.entrySet()) {
            logger.println(entry.getKey() + " -> " + entry.getValue().toString());
        }

        logger.println(" === REFERENCE INDEX === ");
        for (Map.Entry<String, ArrayList<Symbol>> entry : symbolReferencesDB.entrySet()) {
            logger.println(entry.getKey() + " -> " + entry.getValue().size() + " references");
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
        return this.symbolDefinitionsDB.size();
    }

    public void insertSymbolReference(Symbol refersTo, String fileURI, SymbolReferenceNode node) {
        String dbKey = createDBKey(refersTo);
        ArrayList<Symbol> references = symbolReferencesDB.get(dbKey);

        Symbol symbol = new Symbol(node, fileURI);

        if (references != null) {
            references.add(symbol);
            return;
        }

        references = new ArrayList<>() {{
            add(symbol);
        }};

        symbolReferencesDB.put(dbKey, references);
    }
}
