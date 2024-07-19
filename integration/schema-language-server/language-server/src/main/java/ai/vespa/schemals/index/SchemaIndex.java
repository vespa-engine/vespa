package ai.vespa.schemals.index;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.annotationElm;
import ai.vespa.schemals.parser.ast.annotationOutside;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.documentSummary;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldSetElm;
import ai.vespa.schemals.parser.ast.functionElm;
import ai.vespa.schemals.parser.ast.inputName;
import ai.vespa.schemals.parser.ast.namedDocument;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.structDefinitionElm;
import ai.vespa.schemals.parser.ast.structFieldDefinition;
import ai.vespa.schemals.parser.ast.summaryInDocument;

public class SchemaIndex {
    public static final HashMap<Class<?>, SymbolType> IDENTIFIER_TYPE_MAP = new HashMap<>() {{
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
        put(inputName.class, SymbolType.QUERY_INPUT);
    }};

    public static final HashMap<Class<?>, SymbolType> IDENTIFIER_WITH_DASH_TYPE_MAP = new HashMap<>() {{
        put(rankProfile.class, SymbolType.RANK_PROFILE);
        put(documentSummary.class, SymbolType.DOCUMENT_SUMMARY);
    }};

    private PrintStream logger;
    private String workspaceRootURI;

    private Map<SymbolType, List<Symbol>> symbolDefinitions;
    private Map<Symbol, List<Symbol>> symbolReferences;
    private Map<Symbol, Symbol> definitionOfReference;

    private InheritanceGraph<String> documentInheritanceGraph;
    private InheritanceGraph<Symbol> structInheritanceGraph;
    private InheritanceGraph<Symbol> rankProfileInheritanceGraph;
    private InheritanceGraph<Symbol> documentSummaryInheritanceGraph;

    // This is an inheritance graph, even though it doesn't model *inheritance* per se.
    private InheritanceGraph<Symbol> documentReferenceGraph;
    
    public SchemaIndex(PrintStream logger) {
        this.logger = logger;
        this.documentInheritanceGraph        = new InheritanceGraph<>();
        this.structInheritanceGraph          = new InheritanceGraph<>();
        this.rankProfileInheritanceGraph     = new InheritanceGraph<>();
        this.documentSummaryInheritanceGraph = new InheritanceGraph<>();
        this.documentReferenceGraph          = new InheritanceGraph<>();

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
                    documentSummaryInheritanceGraph.clearInheritsList(symbol);
                    documentReferenceGraph.clearInheritsList(symbol);

                    list.remove(i);
                }
            }
        }

        if (fileURI.endsWith(".sd")) {
            documentInheritanceGraph.clearInheritsList(fileURI);
            // Add the node back:)
            documentInheritanceGraph.createNodeIfNotExists(fileURI);
        }
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
        if (results.isEmpty()) return Optional.empty();

        return Optional.of(results.get(0));
    }

    /**
     * Uses symbol.getScope as the scope to search in
     */
    public Optional<Symbol> findSymbolInScope(Symbol symbol) {
        return findSymbolInScope(symbol.getScope(), symbol.getType(), symbol.getShortIdentifier());
    }

    /**
     * Searches for the specified symbol in the given scope. Checks inherited scopes as well,
     * but not containing scopes.
     * */
    public Optional<Symbol> findSymbolInScope(Symbol scope, SymbolType type, String shortIdentifier) {
        List<Symbol> results = findSymbolsInScope(scope, type, shortIdentifier);
        if (results.isEmpty()) return Optional.empty();

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
        // First candidates are all symbols with correct type and correct short identifier

        if (type == SymbolType.SCHEMA || type == SymbolType.DOCUMENT) {
            List<Symbol> schemaDefinitions = 
                symbolDefinitions.get(SymbolType.SCHEMA)
                               .stream()
                               .filter(symbolDefinition -> symbolDefinition.getShortIdentifier().equals(shortIdentifier))
                               .toList();

            if (!schemaDefinitions.isEmpty()) return schemaDefinitions;
            return symbolDefinitions.get(SymbolType.DOCUMENT)
                               .stream()
                               .filter(symbolDefinition -> symbolDefinition.getShortIdentifier().equals(shortIdentifier))
                               .toList();
        }

        // logger.println("Looking for symbol: " + shortIdentifier + " type " + type.toString());
        while (scope != null) {
            // logger.println("  Checking scope: " + scope.getLongIdentifier());
            List<Symbol> result = findSymbolsInScope(scope, type, shortIdentifier);

            if (!result.isEmpty()) {
                return result;
            }
            scope = scope.getScope();
        }

        return new ArrayList<>();
    }

    /*
     * Given a scope, type and short identifier, find definitions defined inside the actual scope. 
     * Will not search inheritance graphs.
     */
    private Optional<Symbol> findSymbolInConcreteScope(Symbol scope, SymbolType type, String shortIdentifier) {
        List<Symbol> match = symbolDefinitions.get(type)
                       .stream()
                       .filter(symbolDefinition -> scope.equals(symbolDefinition.getScope()) && symbolDefinition.getShortIdentifier().equals(shortIdentifier))
                       .toList();
        if (match.isEmpty()) return Optional.empty();
        return Optional.of(match.get(0));
    }


    /*
     * Find symbols with given type and short identifier that are valid in scope
     * Will search in inherited scopes
     */
    public List<Symbol> findSymbolsInScope(Symbol scope, SymbolType type, String shortIdentifier) {
        if (scope.getType() == SymbolType.RANK_PROFILE) {
            return rankProfileInheritanceGraph.findFirstMatches(scope, rankProfileDefinitionSymbol -> {
                var definedInScope = findSymbolInConcreteScope(rankProfileDefinitionSymbol, type, shortIdentifier);
                if (definedInScope.isEmpty()) return null;
                return definedInScope;
            }).stream().map(result -> result.result.get()).toList();
        } else if (scope.getType() == SymbolType.STRUCT) {
            return structInheritanceGraph.findFirstMatches(scope, rankProfileDefinitionSymbol -> {
                var definedInScope = findSymbolInConcreteScope(rankProfileDefinitionSymbol, type, shortIdentifier);
                if (definedInScope.isEmpty()) return null;
                return definedInScope;
            }).stream().map(result -> result.result.get()).toList();
        } else if (scope.getType() == SymbolType.DOCUMENT || scope.getType() == SymbolType.SCHEMA) {
            return documentInheritanceGraph.findFirstMatches(scope.getFileURI(), ancestorURI -> {

                List<Symbol> match = symbolDefinitions.get(type)
                    .stream()
                    .filter(symbolDefinition -> symbolDefinition.getScope() != null
                            && (symbolDefinition.getScope().getType() == SymbolType.SCHEMA || symbolDefinition.getScope().getType() == SymbolType.DOCUMENT)
                            && symbolDefinition.getShortIdentifier().equals(shortIdentifier)
                            && symbolDefinition.getScope().getFileURI().equals(ancestorURI))
                    .toList();

                if (match.isEmpty()) return null;

                return Optional.of(match.get(0));
            }).stream().map(result -> result.result.get()).toList();
        }

        Optional<Symbol> symbol = findSymbolInConcreteScope(scope, type, shortIdentifier);
        List<Symbol> result = new ArrayList<>();
        if (symbol.isPresent())result.add(symbol.get());
        return result;
    }

    public List<Symbol> findSymbolsInScope(Symbol reference) {
        return findSymbolsInScope(reference.getScope(), reference.getType(), reference.getFileURI());
    }

    public boolean isInScope(Symbol symbol, Symbol scope) {
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
        if (reference.getStatus() == SymbolStatus.DEFINITION) return Optional.of(reference);
        return Optional.ofNullable(definitionOfReference.get(reference));
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
     * Deletes the symbol reference from the schema index.
     *
     * @param symbol The symbol reference to be deleted.
     */
    public void deleteSymbolReference(Symbol symbol) {
        Symbol definition = definitionOfReference.remove(symbol);
        if (definition != null) {
            List<Symbol> references = symbolReferences.get(definition);
            if (references != null) {
                references.remove(symbol);
            }
        }
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
     * Tries to register the inheritance relationship between a child document-summary and a parent document-summary.
     *
     * @param childSymbol The symbol representing the child document-summary.
     * @param parentSymbol The symbol representing the parent document-summary.
     * @return true if the inheritance relationship was successfully registered, true otherwise.
     */
    public boolean tryRegisterDocumentSummaryInheritance(Symbol childSymbol, Symbol parentSymbol) {
        return documentSummaryInheritanceGraph.addInherits(childSymbol, parentSymbol);
    }

    public boolean tryRegisterDocumentReference(Symbol childSymbol, Symbol parentSymbol) {
        return documentReferenceGraph.addInherits(childSymbol, parentSymbol);
    }

    /**
     * Searches for symbols in the specified scope of the given type.
     *
     * @param scope The symbol representing the scope to search in.
     * @param type The type of symbols to find.
     * @return A list of symbols found in the specified scope and of the given type.
     */
    public List<Symbol> listSymbolsInScope(Symbol scope, SymbolType type) {
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

        logger.println("\n === SYMBOL DEFINITION REFERENCES === ");
        for (var entry : symbolReferences.entrySet()) {
            logger.println(entry.getKey());

            for (var symbol : entry.getValue()) {
                logger.println("    " + symbol);
            }
        }

        logger.println("\n === REFERENCES TO DEFINITIONS ===");
        for (var entry : definitionOfReference.entrySet()) {
            String toPrint = String.format("%-50s -> %s", entry.getKey(), entry.getValue());
            logger.println(toPrint);
        }

        logger.println("\n === DOCUMENT INHERITANCE === ");
        documentInheritanceGraph.dumpAllEdges(logger);

        logger.println(" === STRUCT INHERITANCE === ");
        structInheritanceGraph.dumpAllEdges(logger);

        logger.println(" === RANK PROFILE INHERITANCE === ");
        rankProfileInheritanceGraph.dumpAllEdges(logger);
    }
}
