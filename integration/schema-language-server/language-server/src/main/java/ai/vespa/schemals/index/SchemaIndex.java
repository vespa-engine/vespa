package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.annotationElm;
import ai.vespa.schemals.parser.ast.annotationOutside;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.documentSummary;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldSetElm;
import ai.vespa.schemals.parser.ast.functionElm;
import ai.vespa.schemals.parser.ast.namedDocument;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.structDefinitionElm;
import ai.vespa.schemals.parser.ast.structFieldDefinition;
import ai.vespa.schemals.parser.ast.summaryInDocument;

public class SchemaIndex {
    public static final HashMap<Class<? extends Node>, SymbolType> IDENTIFIER_TYPE_MAP = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(annotationElm.class, SymbolType.ANNOTATION);
        put(annotationOutside.class, SymbolType.ANNOTATION);
        put(rootSchema.class, SymbolType.SCHEMA);
        put(documentElm.class, SymbolType.DOCUMENT);
        put(namedDocument.class, SymbolType.DOCUMENT);
        put(fieldElm.class, SymbolType.FIELD);
        put(fieldSetElm.class, SymbolType.FIELDSET);
        put(structDefinitionElm.class, SymbolType.STRUCT);
        put(structFieldDefinition.class, SymbolType.FIELD);
        put(functionElm.class, SymbolType.FUNCTION);
    }};

    public static final HashMap<Class<? extends Node>, SymbolType> IDENTIFIER_WITH_DASH_TYPE_MAP = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(rankProfile.class, SymbolType.RANK_PROFILE);
        put(documentSummary.class, SymbolType.DOCUMENT_SUMMARY);
        put(summaryInDocument.class, SymbolType.SUMMARY);
    }};

    private PrintStream logger;
    private String workspaceRootURI;

    private Map<SymbolType, List<Symbol>> symbolDefinitions;
    private Map<Symbol, List<Symbol>> symbolReferences;
    private Map<Symbol, Symbol> definitionOfReference;

    private InheritanceGraph<String> documentInheritanceGraph;
    private InheritanceGraph<Symbol> structInheritanceGraph;
    private InheritanceGraph<Symbol> rankProfileInheritanceGraph;
    
    public SchemaIndex(PrintStream logger) {
        this.logger = logger;
        this.documentInheritanceGraph    = new InheritanceGraph<>();
        this.structInheritanceGraph      = new InheritanceGraph<>();
        this.rankProfileInheritanceGraph = new InheritanceGraph<>();

        this.symbolDefinitions     = new HashMap<>();
        this.symbolReferences      = new HashMap<>();
        this.definitionOfReference = new HashMap<>();

        for (SymbolType type : SymbolType.values()) {
            this.symbolDefinitions.put(type, new ArrayList<Symbol>());
        }

    }

    public void setWorkspaceURI(String workspaceRootURI) {
        this.workspaceRootURI = workspaceRootURI;
    }


    public String getWorkspaceURI() {
        return this.workspaceRootURI;
    }

    /**
     * Returns the inheritance graph for documents in the index.
     *
     * @return the inheritance graph for documents
     */
    public InheritanceGraph<String> getDocumentInheritanceGraph() { return documentInheritanceGraph; }

    /**
     * Clears the index for symbols in the specified file
     *
     * @param fileURI the URI of the document to be cleared
     */
    public void clearDocument(String fileURI) {

        // First: remove symbols listed as a reference for a definition
        // At the same time, remove the reference from the reference -> definition lookup table
        for (Map.Entry<Symbol, List<Symbol>> entry : symbolReferences.entrySet()) {
            List<Symbol> references = entry.getValue();

            List<Symbol> replacedReferences = new ArrayList<>();
            for (Symbol symbol : references) {
                if (symbol.getFileURI().equals(fileURI)) {
                    definitionOfReference.remove(symbol);
                } else {
                    replacedReferences.add(symbol);
                }
            }

            entry.setValue(replacedReferences);
        }

        // For each definition: remove their list of references and then remove the definition itself.
        for (var list : symbolDefinitions.values()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                Symbol symbol = list.get(i);
                if (symbol.getFileURI().equals(fileURI)) {
                    symbolReferences.remove(symbol);

                    structInheritanceGraph.clearInheritsList(symbol);
                    rankProfileInheritanceGraph.clearInheritsList(symbol);

                    list.remove(i);
                }
            }
        }

        documentInheritanceGraph.clearInheritsList(fileURI);
    }

    /**
     * Searches for the specified symbol in the index.
     *
     * @param symbol The symbol to find, should be UNRESOLVED
     * @return An Optional containing the found symbol, or an empty Optional if the symbol is not found.
     */
    public Optional<Symbol> findSymbol(Symbol symbol) {
        return findSymbol(symbol.getScope(), symbol.getType(), symbol.getShortIdentifier());
    }

    public Optional<Symbol> findSymbol(Symbol scope, SymbolType type, String shortIdentifier) {
        List<Symbol> results = findSymbols(scope, type, shortIdentifier);
        if (results.size() == 0) return Optional.empty();

        return Optional.of(results.get(0));
    }

    public List<Symbol> findSymbols(Symbol symbol) {
        return findSymbols(symbol.getScope(), symbol.getType(), symbol.getShortIdentifier());
    }

    /**
     * Searches for symbols in the schema index that match the given symbol.
     *
     * @return A list of symbols that match the given symbol.
     */
    public List<Symbol> findSymbols(Symbol scope, SymbolType type, String shortIdentifier) {
        List<Symbol> result = new ArrayList<>();

        // First candidates are all symbols with correct type and correct short identifier
        List<Symbol> candidates = symbolDefinitions.get(type)
                                                   .stream()
                                                   .filter(symbolDefinition -> symbolDefinition.getShortIdentifier().equals(shortIdentifier))
                                                   .toList();

        if (type == SymbolType.SCHEMA || type == SymbolType.DOCUMENT) {
            return candidates;
        }

        logger.println("LOOKING FOR " + shortIdentifier + " in scope " + (scope == null ? "null" : scope.getLongIdentifier()));
        for (var sym : candidates) {
            logger.println("    Cand: " + sym.getLongIdentifier());
        }

        while (scope != null) {
            logger.println("    Current scope: " + scope.getLongIdentifier());
            for (Symbol candidate : candidates) {
                // Check if candidate is in this scope
                if (isInScope(candidate, scope))result.add(candidate);
            }

            if (!result.isEmpty()) return result;
            scope = scope.getScope();
        }

        return result;
    }

    private boolean isInScope(Symbol symbol, Symbol scope) {
        if (scope.getType() == SymbolType.RANK_PROFILE) {
            return !rankProfileInheritanceGraph.findFirstMatches(scope, 
                    rankProfileDefinitionSymbol -> {
                        if (rankProfileDefinitionSymbol.equals(symbol.getScope())) {
                            return Boolean.valueOf(true);
                        }
                        return null;
            }).isEmpty();
        } else if (scope.getType() == SymbolType.STRUCT) {
            return !structInheritanceGraph.findFirstMatches(scope, 
                    structDefinitionSymbol -> {
                        if (structDefinitionSymbol.equals(symbol.getScope())) {
                            return Boolean.valueOf(true);
                        }
                        return null;
            }).isEmpty();
        } else if ((symbol.getScope() == null || symbol.getScope().getType() == SymbolType.SCHEMA || symbol.getScope().getType() == SymbolType.DOCUMENT) && 
                (scope.getType() == SymbolType.SCHEMA || scope.getType() == SymbolType.DOCUMENT)) {
            return !documentInheritanceGraph.findFirstMatches(scope.getFileURI(), 
                ancestorURI -> {
                    if (symbol.getFileURI().equals(ancestorURI)) {
                        return Boolean.valueOf(true);
                    }
                    return null;
                }
            ).isEmpty();
        }

        return scope.equals(symbol.getScope());
    }

    /**
     * Retrieves the definition of a symbol from a map
     *
     * @param reference The reference to retrieve the definition for.
     * @return An Optional containing the definition of the symbol, or an empty Optional if the symbol is not found.
     */
    public Optional<Symbol> getSymbolDefinition(Symbol reference) {
        Symbol results = definitionOfReference.get(reference);

        if (results == null) return Optional.empty();

        return Optional.of(results);
    }

    /**
     * Returns a list of symbol references for the given symbol definition.
     *
     * @param definition The symbol for which to retrieve the references.
     * @return A list of symbol references.
     */
    public List<Symbol> getSymbolReferences(Symbol definition) {
        List<Symbol> results = symbolReferences.get(definition);

        if (results == null) return new ArrayList<>();

        return results;
    }

    /**
     * Retrieves the symbol definition of the specified schema name.
     *
     * @param shortIdentifier the short identifier of the symbol to retrieve
     * @return an Optional containing the symbol if found, or an empty Optional if not found
     */
    public Optional<Symbol> getSchemaDefinition(String shortIdentifier) {
        List<Symbol> list = symbolDefinitions.get(SymbolType.SCHEMA);

        for (Symbol symbol : list) {
            if (symbol.getShortIdentifier().equals(shortIdentifier)) {
                return Optional.of(symbol);
            }
        }

        list = symbolDefinitions.get(SymbolType.DOCUMENT);
        for (Symbol symbol : list) {
            if (symbol.getShortIdentifier().equals(shortIdentifier)) {
                return Optional.of(symbol);
            }
        }

        return Optional.empty();
    }

    /**
     * Retrieves a list of symbols of the specified type from the schema index.
     *
     * @param type The type of symbols to retrieve.
     * @return A list of symbols of the specified type, or an empty list if no symbols are found.
     */
    public List<Symbol> getSymbolsByType(SymbolType type) {
        return symbolDefinitions.get(type);
    }

    /**
     * Inserts a symbol definition into the schema index.
     *
     * @param symbol the symbol to be inserted
     */
    public void insertSymbolDefinition(Symbol symbol) {

        List<Symbol> list = symbolDefinitions.get(symbol.getType());
        list.add(symbol);

        symbolReferences.put(symbol, new ArrayList<>());
    }

    /**
     * Inserts a symbol reference into the schema index.
     * Make sure that he definition exists before inserting
     *
     * @param definition the symbol being defined
     * @param reference the symbol being referenced
     */
    public void insertSymbolReference(Symbol definition, Symbol reference) {
        List<Symbol> list = symbolReferences.get(definition);
        if (list == null) {
            throw new IllegalArgumentException("Could not insert symbol reference" + reference + " before the definition is inserted");
        }

        definitionOfReference.put(reference, definition);
        list.add(reference);
    }

    /**
     * Tries to register document inheritance between a child document and a parent document.
     *
     * @param childURI  the URI of the child document
     * @param parentURI the URI of the parent document
     * @return true if the document inheritance was successfully registered, false otherwise
     */
    public boolean tryRegisterDocumentInheritance(String childURI, String parentURI) {
        return documentInheritanceGraph.addInherits(childURI, parentURI);
    }

    /**
     * Tries to register struct inheritance between the child symbol and the parent symbol.
     *
     * @param childSymbol The child symbol representing the struct.
     * @param parentSymbol The parent symbol representing the inherited struct.
     * @return false if the struct inheritance was successfully registered, false otherwise.
     */
    public boolean tryRegisterStructInheritance(Symbol childSymbol, Symbol parentSymbol) {
        return structInheritanceGraph.addInherits(childSymbol, parentSymbol);
    }

    /**
     * Tries to register the inheritance relationship between a child rank profile and a parent rank profile.
     *
     * @param childSymbol The symbol representing the child rank profile.
     * @param parentSymbol The symbol representing the parent rank profile.
     * @return true if the inheritance relationship was successfully registered, true otherwise.
     */
    public boolean tryRegisterRankProfileInheritance(Symbol childSymbol, Symbol parentSymbol) {
        return rankProfileInheritanceGraph.addInherits(childSymbol, parentSymbol);
    }

    /**
     * Searches for symbols in the specified scope of the given type.
     *
     * @param scope The symbol representing the scope to search in.
     * @param type The type of symbols to find.
     * @return A list of symbols found in the specified scope and of the given type.
     */
    public List<Symbol> findSymbolsInScope(Symbol scope, SymbolType type) {
        return symbolDefinitions.get(type)
                                .stream()
                                .filter(symbol -> isInScope(symbol, scope))
                                .toList();
    }

    /**
     * Dumps the index to the console.
     */
    public void dumpIndex() {

        logger.println(" === SYMBOL DEFINITIONS === ");
        for (var entry : symbolDefinitions.entrySet()) {
            logger.println("TYPE: " + entry.getKey());

            for (var symbol : entry.getValue()) {
                logger.println("    " + symbol);
            }
        }

        logger.println(" === SYMBOL DEFINITION REFERENCES === ");
        for (var entry : symbolReferences.entrySet()) {
            logger.println(entry.getKey());

            for (var symbol : entry.getValue()) {
                logger.println("    " + symbol);
            }
        }

        logger.println(" === REFERENCES TO DEFINITIONS ===");
        for (var entry : definitionOfReference.entrySet()) {

            logger.println(entry.getKey() + " -> " + entry.getValue());
        }

        logger.println(" === DOCUMENT INHERITANCE === ");
        documentInheritanceGraph.dumpAllEdges(logger);

        logger.println(" === STRUCT INHERITANCE === ");
        structInheritanceGraph.dumpAllEdges(logger);

        logger.println(" === RANK PROFILE INHERITANCE === ");
        rankProfileInheritanceGraph.dumpAllEdges(logger);
    }
}
