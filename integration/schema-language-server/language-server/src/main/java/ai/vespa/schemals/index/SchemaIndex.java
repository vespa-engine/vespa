package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.SchemaNode;

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

    private String createDBKey(String fileURI, SymbolType type, String identifier) {
        return fileURI + ":" + type.name().toLowerCase() + ":" + identifier.toLowerCase(); // identifiers in SD are not sensitive to casing
    }

    public void registerSchema(String fileURI, SchemaDocumentParser schemaDocumentParser) {
        openSchemas.put(fileURI, schemaDocumentParser);
        documentInheritanceGraph.createNodeIfNotExists(fileURI);
    }

    /*
    * Searches for the given symbol ONLY in document pointed to by fileURI
    */
    public Symbol findSymbolInFile(String fileURI, SymbolType type, String identifier) {
        Symbol result = symbolDefinitionsDB.get(createDBKey(fileURI, type, identifier));

        if (result == null) {
            return null;
        }
        return result;
    }

    /*
     * Search for a user defined identifier symbol. Also search in inherited documents.
     */
    public Symbol findSymbol(String fileURI, SymbolType type, String identifier) {
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

    public List<Symbol> findSymbolsWithTypeInDocument(String fileURI, SymbolType type) {
        List<Symbol> result = new ArrayList<>();
        for (var entry : symbolDefinitionsDB.entrySet()) {
            Symbol item = entry.getValue();
            if (!item.getFileURI().equals(fileURI)) continue;
            if (item.getType() != type) continue;
            result.add(item);
        }

        return result;
    }

    public boolean resolveTypeNode(SchemaNode typeNode, String fileURI) {
        // TODO: handle document reference
        String typeName = typeNode.getSymbol().getShortIdentifier();

        // Probably struct
        if (findSymbol(fileURI, SymbolType.STRUCT, typeName.toLowerCase()) != null) {
            typeNode.setSymbolType(SymbolType.STRUCT);
            // typeNode.setSymbolStatus(SymbolStatus.REFERENCE);
            insertSymbolReference(fileURI, typeNode);
            return true;
        }

        // If its not struct it could be document. The document can be defined anywhere
        for (String documentURI : openSchemas.keySet()) {
            if (findSymbolInFile(documentURI, SymbolType.DOCUMENT, typeName.toLowerCase()) != null || 
                  findSymbolInFile(documentURI, SymbolType.SCHEMA, typeName.toLowerCase()) != null) {
                typeNode.setSymbolType(SymbolType.DOCUMENT);
                // typeNode.setSymbolStatus(SymbolStatus.REFERENCE);
                insertSymbolReference(fileURI, typeNode);
                return true;
            }
        }

        return false;
    }

    public boolean resolveAnnotationReferenceNode(SchemaNode annotationReferenceNode, String fileURI) {
        String annotationName = annotationReferenceNode.getText().toLowerCase();

        Symbol referencedSymbol = findSymbol(fileURI, SymbolType.ANNOTATION, annotationName);
        if (referencedSymbol != null) {
            // annotationReferenceNode.setSymbolStatus(SymbolStatus.REFERENCE);
            insertSymbolReference(referencedSymbol, fileURI, annotationReferenceNode);
            return true;
        }

        return false;
    }

    public void dumpIndex(PrintStream logger) {
        logger.println(" === INDEX === ");
        for (Map.Entry<String, Symbol> entry : symbolDefinitionsDB.entrySet()) {
            logger.println(entry.getKey() + " -> " + entry.getValue().toString());
        }

        logger.println(" === REFERENCE INDEX === ");
        for (Map.Entry<String, ArrayList<Symbol>> entry : symbolReferencesDB.entrySet()) {
            String references = "";

            for (int i = 0; i < entry.getValue().size(); i++) {
                if (i > 0) {
                    references += ", ";
                }
                references += entry.getValue().get(i).toString();
            }

            logger.println(entry.getKey() + " -> " + references);
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
        SymbolType[] schemaIdentifierTypes = new SymbolType[] {
            SymbolType.SCHEMA,
            SymbolType.DOCUMENT
        };

        for (SymbolType tokenType : schemaIdentifierTypes) {
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

    public void insertSymbolDefinition(Symbol symbol) {
        String dbKey = createDBKey(symbol);
        symbolDefinitionsDB.put(dbKey, symbol);
    }

    public void insertSymbolReference(Symbol refersTo, String fileURI, SchemaNode node) {
        node.setSymbolStatus(SymbolStatus.REFERENCE);

        String dbKey = createDBKey(refersTo);
        ArrayList<Symbol> references = symbolReferencesDB.get(dbKey);

        Symbol symbol = node.getSymbol();

        if (references == null) {
            references = new ArrayList<>() {{
                add(symbol);
            }};
    
            symbolReferencesDB.put(dbKey, references);
            return;
        }

        if (!references.contains(symbol)) {
            references.add(symbol);
        }
    }

    public void insertSymbolReference(String fileURI, SchemaNode node) {
        Symbol referencedSymbol = findSymbol(fileURI, node.getSymbol().getType(), node.getText());
        insertSymbolReference(referencedSymbol, fileURI, node);
    }
}
