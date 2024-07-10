package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.Optional;

import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.tree.SchemaNode;

public class SchemaIndex {

    private PrintStream logger;
    
    private HashMap<String, Symbol> symbolDefinitionsDB = new HashMap<String, Symbol>();
    private HashMap<String, ArrayList<Symbol>> symbolReferencesDB = new HashMap<>();

    // Map fileURI -> SchemaDocumentParser
    private HashMap<String, SchemaDocumentParser> openSchemas = new HashMap<String, SchemaDocumentParser>();

    private InheritanceGraph<String> documentInheritanceGraph;
    private InheritanceGraph<StructInheritanceNode> structInheritanceGraph;

    // Schema inheritance. Can only inherit one schema
    private HashMap<String, String> schemaInherits = new HashMap<String, String>();
    
    public SchemaIndex(PrintStream logger) {
        this.logger = logger;
        this.documentInheritanceGraph = new InheritanceGraph<>();
        this.structInheritanceGraph = new InheritanceGraph<>();
    }
    
    public void clearDocument(String fileURI) {

        Iterator<Map.Entry<String, Symbol>> iterator = symbolDefinitionsDB.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, Symbol> entry = iterator.next();
            Symbol currentSymbol = entry.getValue();
            if (currentSymbol.getFileURI().equals(fileURI)) {

                // TODO: it's ugly
                if (currentSymbol.getType() == SymbolType.STRUCT) {
                    structInheritanceGraph.clearInheritsList(getStructInheritanceKey(currentSymbol));
                }

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
        return createDBKey(symbol.getFileURI(), symbol.getType(), symbol.getLongIdentifier());
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
        List<String> inheritanceList = documentInheritanceGraph.getAllAncestors(fileURI);

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

    public Optional<Symbol> findDefinitionOfReference(Symbol reference) {
        for (var entry : symbolReferencesDB.entrySet()) {
            for (Symbol registeredReference : entry.getValue()) {
                if (registeredReference == reference) {
                    String key = entry.getKey();
                    return Optional.ofNullable(symbolDefinitionsDB.get(key));
                }
            }
        }
        return Optional.empty();
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

    public List<Symbol> findSymbolsInScope(Symbol scope) {
        return symbolDefinitionsDB.values()
                                  .stream()
                                  .filter(symbol -> symbol.isInScope(scope))
                                  .collect(Collectors.toList());
    }


    public boolean resolveTypeNode(SchemaNode typeNode, String fileURI) {
        // TODO: handle document reference
        String typeName = typeNode.getSymbol().getShortIdentifier();

        // Probably struct
        if (findSymbol(fileURI, SymbolType.STRUCT, typeName.toLowerCase()) != null) {
            typeNode.setSymbolType(SymbolType.STRUCT);
            typeNode.setSymbolStatus(SymbolStatus.REFERENCE);
            return true;
        }

        // If its not struct it could be document. The document can be defined anywhere
        for (String documentURI : openSchemas.keySet()) {
            if (findSymbolInFile(documentURI, SymbolType.DOCUMENT, typeName.toLowerCase()) != null || 
                  findSymbolInFile(documentURI, SymbolType.SCHEMA, typeName.toLowerCase()) != null) {
                typeNode.setSymbolType(SymbolType.DOCUMENT);
                typeNode.setSymbolStatus(SymbolStatus.REFERENCE);
                return true;
            }
        }

        return false;
    }

    public boolean resolveAnnotationReferenceNode(SchemaNode annotationReferenceNode, String fileURI) {
        String annotationName = annotationReferenceNode.getText().toLowerCase();

        if (findSymbol(fileURI, SymbolType.ANNOTATION, annotationName) != null) {
            annotationReferenceNode.setSymbolStatus(SymbolStatus.REFERENCE);
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

        logger.println(" === DOCUMENT INHERITANCE === ");
        documentInheritanceGraph.dumpAllEdges(logger);

        logger.println(" === STRUCT INHERITANCE === ");
        structInheritanceGraph.dumpAllEdges(logger);

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
        return this.documentInheritanceGraph.getAllAncestors(fileURI);
    }

    public List<String> getAllDocumentDescendants(String fileURI) {
        List<String> descendants = this.documentInheritanceGraph.getAllDescendants(fileURI);
        descendants.remove(fileURI);
        return descendants;
    }

    public List<String> getAllDocumentsInInheritanceOrder() {
        return this.documentInheritanceGraph.getTopoOrdering();
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

        if (symbol.getType() == SymbolType.STRUCT) {
            structInheritanceGraph.createNodeIfNotExists(getStructInheritanceKey(symbol));
        }
    }

    public void insertSymbolReference(Symbol refersTo, String fileURI, SchemaNode node) {

        String dbKey = createDBKey(refersTo);
        ArrayList<Symbol> references = symbolReferencesDB.get(dbKey);

        Symbol symbol = node.getSymbol();

        if (references != null) {
            references.add(symbol);
            return;
        }

        references = new ArrayList<>() {{
            add(symbol);
        }};

        symbolReferencesDB.put(dbKey, references);
    }

    // TODO: move these things to somewhere else?

    public List<Symbol> getAllStructFieldSymbols(Symbol structSymbol) {
        if (structSymbol.getStatus() != SymbolStatus.DEFINITION) {
            structSymbol = findSymbol(structSymbol.getFileURI(), SymbolType.STRUCT, structSymbol.getLongIdentifier());
        }
        List<Symbol> result = new ArrayList<>();
        for (StructInheritanceNode structDefinitionNode : structInheritanceGraph.getAllAncestors(getStructInheritanceKey(structSymbol))) {
            Symbol structDefSymbol = structDefinitionNode.getSymbol();
            
            List<Symbol> possibleFieldDefinitions = findSymbolsInScope(structDefSymbol);

            for (Symbol fieldDef : possibleFieldDefinitions) {
                if (fieldDef.getType() == SymbolType.FIELD_IN_STRUCT)result.add(fieldDef);
            }
            
        }
        return result;
    }

    public StructInheritanceNode getStructInheritanceKey(Symbol structSymbol) {
        return new StructInheritanceNode(structSymbol);
    }

    public boolean tryRegisterStructInheritance(Symbol childSymbol, Symbol parentSymbol) {
        StructInheritanceNode childNode = getStructInheritanceKey(childSymbol);
        StructInheritanceNode parentNode = getStructInheritanceKey(parentSymbol);

        return structInheritanceGraph.addInherits(childNode, parentNode);
    }
}
