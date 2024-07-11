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
import ai.vespa.schemals.parser.ast.mapDataType;
import ai.vespa.schemals.parser.indexinglanguage.ast.value;
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

    public Symbol findSymbol(Symbol symbol) {
        return findSymbol(symbol.getFileURI(), symbol.getType(), symbol.getLongIdentifier());
    }

    private ArrayList<Symbol> findSymbolReferences(String dbKey) {
        ArrayList<Symbol> results = symbolReferencesDB.get(dbKey);

        if (results == null) {
            results = new ArrayList<>();
        }

        return results;
    }

    /**
     * Finds symbol references in the schema index for the given symbol.
     *
     * @param symbol The symbol to find references for.
     * @return A list of symbol references found in the schema index.
     */
    public ArrayList<Symbol> findSymbolReferences(Symbol symbol) {
        return findSymbolReferences(createDBKey(symbol));
    }

    public ArrayList<Symbol> findDocumentSymbolReferences(Symbol schemaSymbol) {
        return findSymbolReferences(createDBKey(schemaSymbol.getFileURI(), SymbolType.DOCUMENT, schemaSymbol.getLongIdentifier()));
    }

    public Optional<Symbol> findDefinitionOfReference(Symbol reference) {
        for (var entry : symbolReferencesDB.entrySet()) {
            for (Symbol registeredReference : entry.getValue()) {
                if (registeredReference.equals(reference)) {
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

    public Optional<Symbol> findFieldInStruct(Symbol structDefinitionSymbol, String shortIdentifier) {
        StructInheritanceNode inheritanceNode = getStructInheritanceKey(structDefinitionSymbol);

        for (StructInheritanceNode node : structInheritanceGraph.getAllAncestors(inheritanceNode)) {
            Symbol structSymbol = node.getSymbol();
            Symbol result = findSymbol(structSymbol.getFileURI(), SymbolType.FIELD_IN_STRUCT, structSymbol.getLongIdentifier() + "." + shortIdentifier);
            if (result != null)return Optional.of(result);
        }
        return Optional.empty();
    }

    /**
     * When someone write something.value, find the definition value points to
     * @param fieldDefinitionSymbol The symbol having map as a data type
     * */
    public Optional<Symbol> findMapValueDefinition(Symbol fieldDefinitionSymbol) {
        Symbol rawValueSymbol = findSymbol(fieldDefinitionSymbol.getFileURI(), SymbolType.MAP_VALUE, fieldDefinitionSymbol.getLongIdentifier() + ".value");
        if (rawValueSymbol != null) return Optional.of(rawValueSymbol);

        if (fieldDefinitionSymbol.getType() == SymbolType.FIELD || fieldDefinitionSymbol.getType() == SymbolType.FIELD_IN_STRUCT) {
            SchemaNode dataTypeNode = fieldDefinitionSymbol.getNode().getNextSibling().getNextSibling();
            if (dataTypeNode == null || !dataTypeNode.isASTInstance(dataType.class)) return Optional.empty();
            if (dataTypeNode.get(0) == null || !dataTypeNode.get(0).isASTInstance(mapDataType.class)) return Optional.empty();

            SchemaNode valueNode = dataTypeNode.get(0).get(4);
            if (!valueNode.hasSymbol()) return Optional.empty();

            switch(valueNode.getSymbol().getStatus()) {
                case DEFINITION:
                    return Optional.of(valueNode.getSymbol());
                case REFERENCE:
                    return findDefinitionOfReference(valueNode.getSymbol());
                case INVALID:
                    return Optional.empty();
                case UNRESOLVED:
                    // TODO: figure out if this can happen
                    return Optional.empty();
            }
        } else {
            throw new UnsupportedOperationException("findMapValueDefinition unsupported for type " + fieldDefinitionSymbol.getType().toString());
        }

        return Optional.empty();
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
            insertSymbolReference(referencedSymbol, annotationReferenceNode.getSymbol());
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

    private void insertSymbolReference(String dbKey, Symbol reference) {
        reference.getNode().setSymbolStatus(SymbolStatus.REFERENCE);

        ArrayList<Symbol> content = symbolReferencesDB.get(dbKey);

        if (content == null) {
            content = new ArrayList<>() {{
                add(reference);
            }};
    
            symbolReferencesDB.put(dbKey, content);
            return;
        }

        if (!content.contains(reference)) {
            content.add(reference);
        }
    }

    public void insertSymbolReference(Symbol definition, Symbol reference) {
        if (definition == null) {
            reference.getNode().setSymbolStatus(SymbolStatus.REFERENCE);
            return;
        }

        String dbKey = createDBKey(definition);
        insertSymbolReference(dbKey, reference);
    }

    private void insertDocumentSymbolReference(Symbol reference) {
        Symbol schemaSymbol = findSymbol(reference.getFileURI(), SymbolType.SCHEMA, reference.getLongIdentifier());
        String dbKey = createDBKey(schemaSymbol.getFileURI(), SymbolType.DOCUMENT, schemaSymbol.getLongIdentifier());
        insertSymbolReference(dbKey, reference);
    }

    public void insertSymbolReference(String fileURI, SchemaNode node) {
        Symbol referencedSymbol = findSymbol(fileURI, node.getSymbol().getType(), node.getText());

        if (referencedSymbol == null && node.getSymbol().getType() == SymbolType.DOCUMENT) {
            insertDocumentSymbolReference(node.getSymbol());
        } else {
            insertSymbolReference(referencedSymbol, node.getSymbol());
        }
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
