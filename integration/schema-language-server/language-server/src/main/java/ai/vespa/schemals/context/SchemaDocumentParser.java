package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import com.google.protobuf.Option;
import com.yahoo.tensor.functions.Diag;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.context.parser.Identifier;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.SchemaParser;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.Node;

import ai.vespa.schemals.parser.indexinglanguage.IndexingParser;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParser;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.indexinglanguage.ILUtils;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;

public class SchemaDocumentParser {
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

    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.fileURI = fileURI;
    }
    
    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI, String content) {
        this(logger, diagnosticsHandler, schemaIndex, fileURI);

        if (content != null) {
            updateFileContent(content);
        };
    }

    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI, String content, Integer version) {
        this(logger, diagnosticsHandler, schemaIndex, fileURI, content);
        this.version = version;
        if (version != null) {
            isOpen = true;
        }
    }

    public ParseContext getParseContext(String content) {
        return new ParseContext(content, this.logger, this.fileURI, this.schemaIndex);
    }

    public void reparseContent() {
        if (this.content != null) {
            updateFileContent(this.content, this.version);
        }
    }

    public void updateFileContent(String content, Integer version) {
        this.version = version;
        updateFileContent(content);
    }

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

        logger.println("======== CST for file: " + fileURI + " ========");
        CSTUtils.printTree(logger, CST);

        schemaIndex.dumpIndex(logger);

    }

    public boolean getIsOpen() { return isOpen; }

    boolean setIsOpen(boolean value) {
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

    public static String fileNameFromPath(String path) {
        int splitPos = path.lastIndexOf('/');
        return path.substring(splitPos + 1);
    }

    public String getFileName() {
        return fileNameFromPath(fileURI);
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

    public SchemaNode getNodeAtOrBeforePosition(Position pos) {
        return getNodeAtPosition(CST, pos, false, true);
    }

    public SchemaNode getLeafNodeAtPosition(Position pos) {
        return getNodeAtPosition(CST, pos, true, false);
    }

    public SchemaNode getNodeAtPosition(Position pos) {
        return getNodeAtPosition(CST, pos, false, false);
    }

    public SchemaNode getSymbolAtPosition(Position pos) {
        SchemaNode node = getNodeAtPosition(pos);

        while (node != null && !node.hasSymbol()) {
            node = node.getParent();
        }

        return node;
    }

    private SchemaNode getNodeAtPosition(SchemaNode node, Position pos, boolean onlyLeaf, boolean findNearest) {
        if (node.isLeaf() && CSTUtils.positionInRange(node.getRange(), pos)) {
            return node;
        }

        if (!CSTUtils.positionInRange(node.getRange(), pos)) {
            if (findNearest && !onlyLeaf)return node;
            return null;
        }

        for (SchemaNode child : node) {
            if (CSTUtils.positionInRange(child.getRange(), pos)) {
                return getNodeAtPosition(child, pos, onlyLeaf, findNearest);
            }
        }

        if (onlyLeaf)return null;

        return node;
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
        }

        if (context.inheritsSchemaNode() != null) {
            String inheritsSchemaName = context.inheritsSchemaNode().getText();
            SchemaDocumentParser parent = context.schemaIndex().findSchemaDocumentWithName(inheritsSchemaName);
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
                context.inheritsSchemaNode().setSymbolStatus(SymbolStatus.REFERENCE);
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

    private static Optional<String> resolveDocumentInheritance(SchemaNode inheritanceNode, ParseContext context, List<Diagnostic> diagnostics) {
        String schemaDocumentName = inheritanceNode.getText();
        SchemaDocumentParser parent = context.schemaIndex().findSchemaDocumentWithName(schemaDocumentName);
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
            // dataType is handled separately
            SymbolType referencedType = node.getSymbol().getType();
            Symbol referencedSymbol = context.schemaIndex().findSymbol(context.fileURI(), referencedType, node.getText());
            if (referencedSymbol == null) {
                diagnostics.add(new Diagnostic(
                    node.getRange(),
                    "Undefined symbol " + node.getText(),
                    DiagnosticSeverity.Error,
                    ""
                ));
            } else {
                node.setSymbolStatus(SymbolStatus.REFERENCE);
                context.schemaIndex().insertSymbolReference(referencedSymbol, context.fileURI(), node);
            }
        }

        for (SchemaNode child : node) {
            resolveSymbolReferencesImpl(child, context, diagnostics);
        }
    }

    private static ArrayList<Diagnostic> traverseCST(SchemaNode node, ParseContext context) {

        ArrayList<Diagnostic> ret = new ArrayList<>();
        
        for (Identifier identifier : context.identifiers()) {
            ret.addAll(identifier.identify(node));
        }

        if (node.isIndexingElm()) {
            Range nodeRange = node.getRange();
            var indexingNode = parseIndexingScript(context, node.getILScript(), nodeRange.getStart(), ret);

            if (indexingNode != null) {
                node.setIndexingNode(indexingNode);
            }
        }

        if (node.isFeatureListElm()) {
            Range nodeRange = node.getRange();
            String nodeString = node.get(0).get(0).toString();
            Position featureListStart = CSTUtils.addPositions(nodeRange.getStart(), new Position(0, nodeString.length()));
            var featureListNode = parseFeatureList(context, node.getFeatureListString(), featureListStart, ret);

            if (featureListNode != null) {
                node.setFeatureListNode(featureListNode);
            }
        }

        for (int i = 0; i < node.size(); ++i) {
            ret.addAll(traverseCST(node.get(i), context));
        }

        return ret;
    }

    private static ParseResult parseCST(Node node, ParseContext context) {
        if (node == null) {
            return ParseResult.parsingFailed(new ArrayList<>());
        }
        SchemaNode CST = new SchemaNode(node);
        var errors = traverseCST(CST, context);
        return new ParseResult(errors, Optional.of(CST));
    }

    private static ai.vespa.schemals.parser.indexinglanguage.Node parseIndexingScript(ParseContext context, String script, Position scriptStart, ArrayList<Diagnostic> diagnostics) {
        if (script == null) return null;

        CharSequence sequence = script;
        IndexingParser parser = new IndexingParser(context.logger(), context.fileURI(), sequence);
        parser.setParserTolerant(false);

        try {
            parser.root();
            // TODO: Verify expression
            return parser.rootNode();
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

    private static ai.vespa.schemals.parser.rankingexpression.Node parseFeatureList(ParseContext context, String featureListString, Position listStart, ArrayList<Diagnostic> diagnostics) {
        if (featureListString == null)return null;
        CharSequence sequence = featureListString;

        RankingExpressionParser parser = new RankingExpressionParser(context.logger(), context.fileURI(), sequence);
        parser.setParserTolerant(false);

        try {
            parser.featureList();
            return parser.rootNode();
        } catch(ai.vespa.schemals.parser.rankingexpression.ParseException pe) {
            Range range = RankingExpressionUtils.getNodeRange(pe.getToken());
            range.setStart(CSTUtils.addPositions(listStart, range.getStart()));
            range.setEnd(CSTUtils.addPositions(listStart, range.getEnd()));

            diagnostics.add(new Diagnostic(range, pe.getMessage()));
        } catch(IllegalArgumentException ex) {
            // TODO: diagnostics
        }

        return null;
    }


    /*
     * If necessary, the following methods can be sped up by
     * selecting an appropriate data structure.
     * */
    private int positionToOffset(Position pos) {
        List<String> lines = content.lines().toList();
        if (pos.getLine() >= lines.size())throw new IllegalArgumentException("Line " + pos.getLine() + " out of range for document " + fileURI);

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

    private Position offsetToPosition(int offset) {
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

    public String toString() {
        String openString = getIsOpen() ? " [OPEN]" : "";
        return getFileURI() + openString;
    }
}
