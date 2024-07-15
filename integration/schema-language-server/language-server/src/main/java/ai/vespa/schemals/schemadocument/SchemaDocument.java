package ai.vespa.schemals.schemadocument;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.SchemaDiagnosticsHandler;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.common.FileUtils;

import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.parser.SchemaParser;
import ai.vespa.schemals.parser.SubLanguageData;
import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.dataType;


import ai.vespa.schemals.parser.indexinglanguage.IndexingParser;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SchemaNode.LanguageType;
import ai.vespa.schemals.tree.indexinglanguage.ILUtils;

public class SchemaDocument implements DocumentManager {
    public record ParseResult(ArrayList<Diagnostic> diagnostics, Optional<SchemaNode> CST) {
        public static ParseResult parsingFailed(ArrayList<Diagnostic> diagnostics) {
            return new ParseResult(diagnostics, Optional.empty());
        }
    }

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;

    private String fileURI = "";
    private Integer version;
    private boolean isOpen = false;
    private String content = "";
    private String schemaDocumentIdentifier = null;
    
    private SchemaNode CST;

    public SchemaDocumentLexer lexer = new SchemaDocumentLexer();

    public SchemaDocument(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.fileURI = fileURI;
    }
    
    public ParseContext getParseContext(String content) {
        ParseContext context = new ParseContext(content, this.logger, this.fileURI, this.schemaIndex);
        context.useDocumentIdentifiers();
        return context;
    }

    @Override
    public void reparseContent() {
        if (this.content != null) {
            updateFileContent(this.content, this.version);
        }
    }

    @Override
    public void updateFileContent(String content, Integer version) {
        this.version = version;
        updateFileContent(content);
    }

    @Override
    public void updateFileContent(String content) {
        this.content = content;
        schemaIndex.clearDocument(fileURI);
        schemaIndex.registerSchema(fileURI, this);

        ParseContext context = getParseContext(content);
        var parsingResult = parseContent(context);

        Symbol schemaIdentifier = schemaIndex.findSchemaIdentifierSymbol(fileURI);

        if (schemaIdentifier != null) {
            schemaDocumentIdentifier = schemaIdentifier.getShortIdentifier();

            if (!getFileName().equals(schemaDocumentIdentifier + ".sd")) {
                // TODO: quickfix
                parsingResult.diagnostics().add(new Diagnostic(
                    schemaIdentifier.getNode().getRange(),
                    "Schema " + schemaDocumentIdentifier + " should be defined in a file with the name: " + schemaDocumentIdentifier + ".sd. File name is: " + getFileName(),
                    DiagnosticSeverity.Error,
                    ""
                ));
            }
        }

        diagnosticsHandler.publishDiagnostics(fileURI, parsingResult.diagnostics());

        if (parsingResult.CST().isPresent()) {
            this.CST = parsingResult.CST().get();
            lexer.setCST(CST);
        }

        logger.println("Parsing: " + fileURI);

        logger.println("======== CST for file: " + fileURI + " ========");
 
        CSTUtils.printTree(logger, CST);

        schemaIndex.dumpIndex(logger);

    }

    @Override
    public boolean getIsOpen() { return isOpen; }

    @Override
    public boolean setIsOpen(boolean value) {
        isOpen = value;
        return isOpen;
    }

    public String getSchemaIdentifier() {
        return schemaDocumentIdentifier;
    }

    public String getFileURI() {
        return fileURI;
    }

    public Integer getVersion() {
        return version;
    }

    public VersionedTextDocumentIdentifier getVersionedTextDocumentIdentifier() {
        return new VersionedTextDocumentIdentifier(fileURI, null);
    }

    public String getFilePath() {
        int splitPos = fileURI.lastIndexOf('/');
        return fileURI.substring(0, splitPos + 1);
    }

    public String getFileName() {
        return FileUtils.fileNameFromPath(fileURI);
    }

    public Position getPreviousStartOfWord(Position pos) {
        int offset = positionToOffset(pos);

        // Skip whitespace
        // But not newline because newline is a token
        while (offset >= 0 && Character.isWhitespace(content.charAt(offset)))offset--;

        for (int i = offset; i >= 0; i--) {
            if (Character.isWhitespace(content.charAt(i)))return offsetToPosition(i + 1);
        }

        return null;
    }

    public boolean isInsideComment(Position pos) {
        int offset = positionToOffset(pos);

        if (content.charAt(offset) == '\n')offset--;

        for (int i = offset; i >= 0; i--) {
            if (content.charAt(i) == '\n')break;
            if (content.charAt(i) == '#')return true;
        }
        return false;
    }

    public SchemaNode getRootNode() {
        return CST;
    }


    public static ParseResult parseContent(ParseContext context) {
        CharSequence sequence = context.content();

        SchemaParser parserStrict = new SchemaParser(context.logger(), context.fileURI(), sequence);
        parserStrict.setParserTolerant(false);

        ArrayList<Diagnostic> diagnostics = new ArrayList<Diagnostic>();

        try {
            parserStrict.Root();
        } catch (ParseException e) {

            Node.TerminalNode node = e.getToken();

            Range range = CSTUtils.getNodeRange(node);
            String message = e.getMessage();

            diagnostics.add(new Diagnostic(range, message));


        } catch (IllegalArgumentException e) {
            // Complex error, invalidate the whole document

            diagnostics.add(
                new Diagnostic(
                    new Range(
                        new Position(0, 0),
                        new Position((int)context.content().lines().count() - 1, 0)
                    ),
                    e.getMessage())
                );
            return ParseResult.parsingFailed(diagnostics);
        }

        SchemaParser parserFaultTolerant = new SchemaParser(context.fileURI(), sequence);
        try {
            parserFaultTolerant.Root();
        } catch (ParseException e) {
            // Ignore
        } catch (IllegalArgumentException e) {
            // Ignore
        }

        Node node = parserFaultTolerant.rootNode();

        var tolerantResult = parseCST(node, context);

        diagnostics.addAll(resolveInheritances(context));

        for (SchemaNode typeNode : context.unresolvedTypeNodes()) {
            context.schemaIndex().resolveTypeNode(typeNode, context.fileURI());
        }
        context.clearUnresolvedTypeNodes();

        for (SchemaNode annotationReferenceNode : context.unresolvedAnnotationReferenceNodes()) {
            context.schemaIndex().resolveAnnotationReferenceNode(annotationReferenceNode, context.fileURI());
        }
        context.clearUnresolvedAnnotationReferenceNodes();

        if (tolerantResult.CST().isPresent()) {
            diagnostics.addAll(resolveSymbolReferences(context, tolerantResult.CST().get()));
        }

        diagnostics.addAll(tolerantResult.diagnostics());

        return new ParseResult(diagnostics, tolerantResult.CST());
    }

    /*
     * Assuming first parsing step is done, use the list of unresolved inheritance
     * declarations to register the inheritance at index.
     * @return List of diagnostic found during inheritance handling
     * */
    private static List<Diagnostic> resolveInheritances(ParseContext context) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Set<String> documentInheritanceURIs = new HashSet<>();

        for (SchemaNode inheritanceNode : context.unresolvedInheritanceNodes()) {
            if (inheritanceNode.getSymbol().getType() == SymbolType.DOCUMENT) {
                resolveDocumentInheritance(inheritanceNode, context, diagnostics).ifPresent(
                    parentURI -> documentInheritanceURIs.add(parentURI)
                );
            }
        }

        for (SchemaNode inheritanceNode : context.unresolvedInheritanceNodes()) {
            if (inheritanceNode.getSymbol().getType() == SymbolType.STRUCT) {
                resolveStructInheritance(inheritanceNode, context, diagnostics);
            }

            if (inheritanceNode.getSymbol().getType() == SymbolType.RANK_PROFILE) {
                resolveRankProfileInheritance(inheritanceNode, context, diagnostics);
            }
        }

        if (context.inheritsSchemaNode() != null) {
            String inheritsSchemaName = context.inheritsSchemaNode().getText();
            SchemaDocument parent = context.schemaIndex().findSchemaDocumentWithName(inheritsSchemaName);
            if (parent != null) {
                if (!documentInheritanceURIs.contains(parent.getFileURI())) {
                    // TODO: quickfix
                    diagnostics.add(new Diagnostic(
                        context.inheritsSchemaNode().getRange(),
                        "The schema document must explicitly inherit from " + inheritsSchemaName + " because the containing schema does so.",
                        DiagnosticSeverity.Error,
                        ""
                    ));
                    context.schemaIndex().setSchemaInherits(context.fileURI(), parent.getFileURI());
                }
                context.schemaIndex().insertSymbolReference(context.fileURI(), context.inheritsSchemaNode());
            }
        }

        context.clearUnresolvedInheritanceNodes();

        return diagnostics;
    }

    private static void resolveStructInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        SchemaNode myStructDefinitionNode = inheritanceNode.getParent().getPreviousSibling();
        String inheritedIdentifier = inheritanceNode.getText();

        if (myStructDefinitionNode == null) {
            return;
        }

        if (!myStructDefinitionNode.hasSymbol()) {
            return;
        }

        Symbol parentSymbol = context.schemaIndex().findSymbol(context.fileURI(), SymbolType.STRUCT, inheritedIdentifier);
        if (parentSymbol == null) {
            // Handled elsewhere
            return;
        }

        if (!context.schemaIndex().tryRegisterStructInheritance(myStructDefinitionNode.getSymbol(), parentSymbol)) {
            // TODO: get the chain?
            diagnostics.add(new Diagnostic(
                inheritanceNode.getRange(), 
                "Cannot inherit from " + parentSymbol.getShortIdentifier() + " because " + parentSymbol.getShortIdentifier() + " inherits from this struct.",
                DiagnosticSeverity.Error, 
                ""
            ));
        }


        // Look for redeclarations
        Set<String> fieldsSeen = new HashSet<>();

        for (Symbol fieldSymbol : context.schemaIndex().getAllStructFieldSymbols(myStructDefinitionNode.getSymbol())) {
            if (fieldsSeen.contains(fieldSymbol.getShortIdentifier())) {
                // TODO: quickfix
                diagnostics.add(new Diagnostic(
                    fieldSymbol.getNode().getRange(),
                    "struct " + myStructDefinitionNode.getText() + " cannot inherit from " + parentSymbol.getShortIdentifier() + " and redeclare field " + fieldSymbol.getShortIdentifier(),
                    DiagnosticSeverity.Error,
                    ""
                ));
            }
            fieldsSeen.add(fieldSymbol.getShortIdentifier().toLowerCase());
        }
    }

    private static void resolveRankProfileInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        SchemaNode myRankProfileDefinitionNode = inheritanceNode.getParent().getPreviousSibling();
        String inheritedIdentifier = inheritanceNode.getText();

        if (myRankProfileDefinitionNode == null) return;
        if (!myRankProfileDefinitionNode.hasSymbol() || myRankProfileDefinitionNode.getSymbol().getStatus() != SymbolStatus.DEFINITION) return;

        if (inheritedIdentifier.equals("default")) {
            // TODO: mechanism for inheriting default rank profile. 
            // Workaround now: 
            inheritanceNode.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
            return;
        }


        List<Symbol> parentSymbols = context.schemaIndex().findAllSymbolsWithSchemaScope(context.fileURI(), SymbolType.RANK_PROFILE, inheritedIdentifier);

        if (parentSymbols.isEmpty()) {
            // Handled in resolve symbol ref
            return;
        }

        if (parentSymbols.size() > 1 && !parentSymbols.get(0).getFileURI().equals(context.fileURI())) {
            String note = "\nNote:";

            for (Symbol symbol : parentSymbols) {
                note += "\nDefined in " + FileUtils.fileNameFromPath(symbol.getFileURI());
            }

            diagnostics.add(new Diagnostic(
                inheritanceNode.getRange(),
                inheritedIdentifier + " is ambiguous in this context.",
                DiagnosticSeverity.Warning,
                note
            ));
        }

        // Choose last one, if more than one (undefined behaviour if ambiguous).
        Symbol parentSymbol = parentSymbols.get(0);

        Symbol definitionSymbol = myRankProfileDefinitionNode.getSymbol();
        if (!context.schemaIndex().tryRegisterRankProfileInheritance(definitionSymbol, parentSymbol)) {
            diagnostics.add(new Diagnostic(
                inheritanceNode.getRange(),
                "Cannot inherit from " + parentSymbol.getShortIdentifier() + " because " + parentSymbol.getShortIdentifier() + " inherits from this rank profile.", 
                DiagnosticSeverity.Error,
                ""
            ));

            return;
        }

        List<Symbol> parentDefinitions = context.schemaIndex().getAllRankProfileParents(definitionSymbol);

        /*
         * Look for colliding function names
         * TODO: other stuff than functions
         */
        Map<String, String> seenFunctions = new HashMap<>();
        for (Symbol parentDefinition : parentDefinitions) {
            if (parentDefinition.equals(definitionSymbol)) continue;

            List<Symbol> functionDefinitionsInParent = context.schemaIndex().getAllRankProfileFunctions(parentDefinition);

            context.logger().println("PROFILE " + parentDefinition.getLongIdentifier());
            for (Symbol func : functionDefinitionsInParent) {
                context.logger().println("    FUNC: " + func.getLongIdentifier());
                if (seenFunctions.containsKey(func.getShortIdentifier())) {
                    // TODO: quickfix
                    diagnostics.add(new Diagnostic(
                        inheritanceNode.getRange(),
                        "Cannot inherit from " + parentSymbol.getShortIdentifier() + " because " + parentSymbol.getShortIdentifier() + 
                        " defines function " + func.getShortIdentifier() + " which is already defined in " + seenFunctions.get(func.getShortIdentifier()),
                        DiagnosticSeverity.Error,
                        ""
                    ));
                }
                seenFunctions.put(func.getShortIdentifier(), parentDefinition.getLongIdentifier());
            }
        }
    }

    private static Optional<String> resolveDocumentInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        String schemaDocumentName = inheritanceNode.getText();
        SchemaDocument parent = context.schemaIndex().findSchemaDocumentWithName(schemaDocumentName);
        if (parent == null) {
            // Handled in resolve symbol references
            return Optional.empty();
        }
        if (!context.schemaIndex().tryRegisterDocumentInheritance(context.fileURI(), parent.getFileURI())) {
            // Inheritance cycle
            // TODO: quickfix, get the chain?
            diagnostics.add(new Diagnostic(
                inheritanceNode.getRange(),
                "Cannot inherit from " + schemaDocumentName + " because " + schemaDocumentName + " inherits from this document.",
                DiagnosticSeverity.Error,
                ""
            ));
            return Optional.empty();
        }

        inheritanceNode.setSymbolStatus(SymbolStatus.REFERENCE);
        context.schemaIndex().insertSymbolReference(context.fileURI(), inheritanceNode);
        return Optional.of(parent.getFileURI());
    }

    private static List<Diagnostic> resolveSymbolReferences(ParseContext context, SchemaNode CST) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        resolveSymbolReferencesImpl(CST, context, diagnostics);
        return diagnostics;
    }

    /*
     * Traverse the CST, yet again, to find symbol references and look them up in the index
     */
    private static void resolveSymbolReferencesImpl(SchemaNode node, ParseContext context, List<Diagnostic> diagnostics) {
        if (node.hasSymbol() && node.getSymbol().getStatus() == SymbolStatus.UNRESOLVED) {
            Optional<Symbol> referencedSymbol = Optional.empty();
            // dataType is handled separately
            SymbolType referencedType = node.getSymbol().getType();
            if (referencedType == SymbolType.SUBFIELD) {
                SchemaNode parentField = node.getPreviousSibling();
                Optional<Symbol> parentFieldDefinition = Optional.empty();

                // Two cases for where the parent field is defined. Either inside a struct or "global". 
                if (parentField.hasSymbol() && parentField.getSymbol().getType() == SymbolType.FIELD && parentField.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
                    parentFieldDefinition = Optional.ofNullable(context.schemaIndex().findSymbol(context.fileURI(), SymbolType.FIELD, parentField.getText()));

                } else if (parentField.hasSymbol() && parentField.getSymbol().getType() == SymbolType.FIELD_IN_STRUCT && parentField.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
                    parentFieldDefinition = context.schemaIndex().findDefinitionOfReference(parentField.getSymbol());
                } else if (parentField.hasSymbol() && parentField.getSymbol().getType() == SymbolType.MAP_VALUE && parentField.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
                    parentFieldDefinition = context.schemaIndex().findDefinitionOfReference(parentField.getSymbol());
                } else if (parentField.hasSymbol() && parentField.getSymbol().getType() == SymbolType.STRUCT && parentField.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
                    parentFieldDefinition = context.schemaIndex().findDefinitionOfReference(parentField.getSymbol());
                }

                if (parentFieldDefinition.isPresent()) {
                    referencedSymbol = resolveSubFieldReference(node, parentFieldDefinition.get(), context);
                }
            } else if (referencedType == SymbolType.RANK_PROFILE) {
                referencedSymbol = Optional.ofNullable(context.schemaIndex().findSymbolWithSchemaScope(node.getSymbol()));
            } else {
                referencedSymbol = Optional.ofNullable(context.schemaIndex().findSymbol(node.getSymbol()));
            }

            if (referencedSymbol.isPresent()) {
                node.setSymbolStatus(SymbolStatus.REFERENCE);
                context.schemaIndex().insertSymbolReference(referencedSymbol.get(), node.getSymbol());
            } else {
                diagnostics.add(new Diagnostic(
                    node.getRange(),
                    "Undefined symbol " + node.getText(),
                    DiagnosticSeverity.Error,
                    ""
                ));
            }
        }

        for (SchemaNode child : node) {
            resolveSymbolReferencesImpl(child, context, diagnostics);
        }

        //if (node.hasSymbol()) {
        //    diagnostics.add(new Diagnostic(node.getRange(), node.getSymbol().getLongIdentifier(), DiagnosticSeverity.Information, node.getSymbol().getType().toString() + " " + node.getSymbol().getStatus().toString()));
        //}

    }

    /**
     * This finds the definition of a field inside a struct, where the struct is used as a type for some parent field
     * It solves the case where we have 
     * struct foo { field bar }
     * field baz type foo {}
     * And try to access baz.bar
     *
     * In this case baz is the parent field and bar is the subfield
     *
     * @param node the node pointing to the subfield declaration 
     * @param fieldDefinition the symbol where the parent field is *defined*
     * @param context 
     *
     * @return the definition of the field inside the struct if found 
     */
    private static Optional<Symbol> resolveSubFieldReference(SchemaNode node, Symbol fieldDefinition, ParseContext context) {
        if (fieldDefinition.getType() != SymbolType.FIELD 
            && fieldDefinition.getType() != SymbolType.FIELD_IN_STRUCT 
            && fieldDefinition.getType() != SymbolType.MAP_VALUE
            && fieldDefinition.getType() != SymbolType.STRUCT) return Optional.empty();
        if (fieldDefinition.getStatus() != SymbolStatus.DEFINITION) return Optional.empty();

        if (fieldDefinition.getType() == SymbolType.STRUCT) {
            return resolveFieldInStructReference(node, fieldDefinition, context);
        }

        SchemaNode dataTypeNode = null;
        Optional<Symbol> referencedSymbol = Optional.empty();
        if (fieldDefinition.getType() == SymbolType.MAP_VALUE) {
            dataTypeNode = fieldDefinition.getNode();
        } else if (fieldDefinition.getType() == SymbolType.FIELD || fieldDefinition.getType() == SymbolType.FIELD_IN_STRUCT) {
            dataTypeNode = fieldDefinition.getNode().getNextSibling().getNextSibling();
            if (!dataTypeNode.isASTInstance(dataType.class)) return Optional.empty();


            if (dataTypeNode.hasSymbol()) {
                // TODO: handle annotation reference and document reference?
                if (!isStructReference(dataTypeNode)) return Optional.empty();

                Symbol structReference = dataTypeNode.getSymbol();
                Symbol structDefinition = context.schemaIndex().findSymbol(context.fileURI(), SymbolType.STRUCT, structReference.getLongIdentifier());
                return resolveFieldInStructReference(node, structDefinition, context);
            }
        } else {
            return Optional.empty();
        }


        dataType originalNode = (dataType)dataTypeNode.getOriginalSchemaNode();
        if (originalNode.getParsedType().getVariant() == Variant.MAP) {
            return resolveMapValueReference(node, fieldDefinition, context);
        } else if (originalNode.getParsedType().getVariant() == Variant.ARRAY) {
            if (dataTypeNode.size() < 3 || !dataTypeNode.get(2).isASTInstance(dataType.class)) return Optional.empty();

            SchemaNode innerType = dataTypeNode.get(2);
            if (!isStructReference(innerType)) return Optional.empty();

            Symbol structReference = innerType.getSymbol();
            Symbol structDefinition = context.schemaIndex().findSymbol(context.fileURI(), SymbolType.STRUCT, structReference.getLongIdentifier());

            if (structDefinition == null) return Optional.empty();

            return resolveFieldInStructReference(node, structDefinition, context);
        }
        return referencedSymbol;
    }

    private static Optional<Symbol> resolveFieldInStructReference(SchemaNode node, Symbol structDefinition, ParseContext context) {
        Optional<Symbol> referencedSymbol = Optional.empty();
        referencedSymbol = context.schemaIndex().findFieldInStruct(structDefinition, node.getText());
        //referencedSymbol = Optional.ofNullable(context.schemaIndex().findSymbol(context.fileURI(), SymbolType.FIELD_IN_STRUCT, structDefinition.getLongIdentifier() + "." + node.getText()));
        referencedSymbol.ifPresent(symbol -> node.setSymbolType(symbol.getType()));
        return referencedSymbol;
    }

    private static Optional<Symbol> resolveMapValueReference(SchemaNode node, Symbol mapValueDefinition, ParseContext context) {
        Optional<Symbol> referencedSymbol = Optional.empty();
        if (node.getText().equals("key")) {
            referencedSymbol = Optional.ofNullable(
                context.schemaIndex().findSymbol(context.fileURI(), SymbolType.MAP_KEY, mapValueDefinition.getLongIdentifier() + ".key")
            );

            referencedSymbol.ifPresent(symbol -> node.setSymbolType(SymbolType.MAP_KEY));
        }
        if (node.getText().equals("value")) {
            // For value there are two cases: either the map value is a primitive value with its own definition
            // or it is a reference to a struct
            referencedSymbol = context.schemaIndex().findMapValueDefinition(mapValueDefinition);

            referencedSymbol.ifPresent(symbol -> node.setSymbolType(symbol.getType()));
        }
        return referencedSymbol;
    }

    private static boolean isStructReference(SchemaNode node) {
        return node != null && node.hasSymbol() && node.getSymbol().getType() == SymbolType.STRUCT && node.getSymbol().getStatus() == SymbolStatus.REFERENCE;
    }

    private static ArrayList<Diagnostic> traverseCST(SchemaNode node, ParseContext context) {


        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (node.containsOtherLanguageData(LanguageType.INDEXING)) {
            Range nodeRange = node.getRange();
            SchemaNode indexingNode = parseIndexingScript(context, node.getILScript(), nodeRange.getStart(), ret);
            if (indexingNode != null) {
                node.addChild(indexingNode);
            }
        }

        if (node.containsOtherLanguageData(LanguageType.RANK_EXPRESSION)) {
            // Range nodeRange = node.getRange();
            // String nodeString = node.get(0).get(0).getText();
            // Position rankExpressionStart = CSTUtils.addPositions(nodeRange.getStart(), new Position(0, nodeString.length()));

            SchemaRankExpressionParser.embedCST(context, node, ret);
        }

        for (Identifier identifier : context.identifiers()) {
            ret.addAll(identifier.identify(node));
        }

        for (int i = 0; i < node.size(); ++i) {
            ret.addAll(traverseCST(node.get(i), context));
        }

        return ret;
    }

    public static ParseResult parseCST(Node node, ParseContext context) {
        if (node == null) {
            return ParseResult.parsingFailed(new ArrayList<>());
        }
        SchemaNode CST = new SchemaNode(node);
        var errors = traverseCST(CST, context);
        return new ParseResult(errors, Optional.of(CST));
    }

    private static SchemaNode parseIndexingScript(ParseContext context, SubLanguageData script, Position indexingStart, ArrayList<Diagnostic> diagnostics) {
        if (script == null) return null;

        CharSequence sequence = script.content();
        IndexingParser parser = new IndexingParser(context.logger(), context.fileURI(), sequence);
        parser.setParserTolerant(false);

        Position scriptStart = PositionAddOffset(context.content(), indexingStart, script.leadingStripped());

        try {
            parser.root();
            // TODO: Verify expression
            return new SchemaNode(parser.rootNode(), indexingStart);
        } catch(ai.vespa.schemals.parser.indexinglanguage.ParseException pe) {
            context.logger().println("Encountered parsing error in parsing feature list");
            Range range = ILUtils.getNodeRange(pe.getToken());
            range.setStart(CSTUtils.addPositions(scriptStart, range.getStart()));
            range.setEnd(CSTUtils.addPositions(scriptStart, range.getEnd()));

            diagnostics.add(new Diagnostic(range, pe.getMessage()));
        } catch(IllegalArgumentException ex) {
            context.logger().println("Encountered unknown error in parsing ILScript: " + ex.getMessage());
        }

        return null;
    }

    /*
     * If necessary, the following methods can be sped up by
     * selecting an appropriate data structure.
     * */
    private static int positionToOffset(String content, Position pos) {
        List<String> lines = content.lines().toList();
        if (pos.getLine() >= lines.size())throw new IllegalArgumentException("Line " + pos.getLine() + " out of range.");

        int lineCounter = 0;
        int offset = 0;
        for (String line : lines) {
            if (lineCounter == pos.getLine())break;
            offset += line.length() + 1; // +1 for line terminator
            lineCounter += 1;
        }

        if (pos.getCharacter() > lines.get(pos.getLine()).length())throw new IllegalArgumentException("Character " + pos.getCharacter() + " out of range for line " + pos.getLine());

        offset += pos.getCharacter();

        return offset;
    }

    private int positionToOffset(Position pos) {
        return positionToOffset(content, pos);
    }

    private static Position offsetToPosition(String content, int offset) {
        List<String> lines = content.lines().toList();
        int lineCounter = 0;
        for (String line : lines) {
            int lengthIncludingTerminator = line.length() + 1;
            if (offset < lengthIncludingTerminator) {
                return new Position(lineCounter, offset);
            }
            offset -= lengthIncludingTerminator;
            lineCounter += 1;
        }
        return null;
    }

    private Position offsetToPosition(int offset) {
        return offsetToPosition(content, offset);
    }

    static Position PositionAddOffset(String content, Position pos, int offset) {
        int totalOffset = positionToOffset(content, pos) + offset;
        return offsetToPosition(content, totalOffset);
    }

    public String toString() {
        String openString = getIsOpen() ? " [OPEN]" : "";
        return getFileURI() + openString;
    }
}
