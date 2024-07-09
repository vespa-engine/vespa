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

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.context.parser.Identifier;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.parser.SchemaParser;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.Node;

import ai.vespa.schemals.parser.indexinglanguage.IndexingParser;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParser;
import ai.vespa.schemals.tree.AnnotationReferenceNode;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SymbolNode;
import ai.vespa.schemals.tree.SymbolReferenceNode;
import ai.vespa.schemals.tree.TypeNode;
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

        CSTUtils.printTree(logger, CST);

        //schemaIndex.dumpIndex(logger);

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
        return new VersionedTextDocumentIdentifier(fileURI, version);
    }

    public String getFileName() {
        Integer splitPos = fileURI.lastIndexOf('/');
        return fileURI.substring(splitPos + 1);
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

    public SymbolNode getSymbolAtPosition(Position pos) {
        SchemaNode node = getNodeAtPosition(pos);

        while (node != null && !(node instanceof SymbolNode)) {
            node = node.getParent();
        }

        return (SymbolNode)node;
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

        for (TypeNode typeNode : context.unresolvedTypeNodes()) {
            if (!context.schemaIndex().resolveTypeNode(typeNode, context.fileURI())) {
                diagnostics.add(new Diagnostic(
                    typeNode.getRange(),
                    "Unknown type " + typeNode.getText(),
                    DiagnosticSeverity.Error,
                    ""
                ));
            }
        }
        context.clearUnresolvedTypeNodes();

        for (AnnotationReferenceNode annotationReferenceNode : context.unresolvedAnnotationReferenceNodes()) {
            if (!context.schemaIndex().resolveAnnotationReferenceNode(annotationReferenceNode, context.fileURI())) {
                diagnostics.add(new Diagnostic(
                    annotationReferenceNode.getRange(),
                    "Unknown annotation: " + annotationReferenceNode.getText(),
                    DiagnosticSeverity.Error,
                    ""
                ));
            }
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

        for (SchemaNode schemaDocumentNameNode : context.unresolvedInheritanceNodes()) {
            String schemaDocumentName = schemaDocumentNameNode.getText();
            SchemaDocumentParser parent = context.schemaIndex().findSchemaDocumentWithName(schemaDocumentName);
            if (parent == null) {
                diagnostics.add(new Diagnostic(
                    schemaDocumentNameNode.getRange(),
                    "Unknown document " + schemaDocumentName,
                    DiagnosticSeverity.Error,
                    ""
                ));
            } else {
                if (!context.schemaIndex().tryRegisterDocumentInheritance(context.fileURI(), parent.getFileURI())) {
                    // Inheritance cycle
                    // TODO: quickfix
                    diagnostics.add(new Diagnostic(
                        schemaDocumentNameNode.getRange(),
                        "Cannot inherit from " + schemaDocumentName + " because " + schemaDocumentName + " inherits from this document. This would cause an inheritance cycle and is not allowed.",
                        DiagnosticSeverity.Error,
                        ""
                    ));
                } else {
                    documentInheritanceURIs.add(parent.getFileURI());
                }
            }
        }

        if (context.inheritsSchemaNode() != null) {
            String inheritsSchemaName = context.inheritsSchemaNode().getText();
            SchemaDocumentParser parent = context.schemaIndex().findSchemaDocumentWithName(inheritsSchemaName);
            if (parent == null) {
                diagnostics.add(new Diagnostic(
                    context.inheritsSchemaNode().getRange(),
                    "Unknown schema " + inheritsSchemaName,
                    DiagnosticSeverity.Error,
                    ""
                ));
            } else if (!documentInheritanceURIs.contains(parent.getFileURI())) {
                // TODO: Quickfix
                diagnostics.add(new Diagnostic(
                    context.inheritsSchemaNode().getRange(),
                    "The schema document must explicitly inherit from " + inheritsSchemaName + " because the containing schema does so.",
                    DiagnosticSeverity.Error,
                    ""
                ));
            } else {
                context.schemaIndex().setSchemaInherits(context.fileURI(), parent.getFileURI());
            }
        }

        context.clearUnresolvedInheritanceNodes();

        return diagnostics;
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
        if (node instanceof SymbolReferenceNode) {
            TokenType referencedType = ((SymbolReferenceNode)node).getSymbolType();
            if (context.schemaIndex().findSymbol(context.fileURI(), referencedType, node.getText()) == null) {
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

        for (SchemaNode child : node) {
            ret.addAll(traverseCST(child, context));
        }

        return ret;
    }

    private static ParseResult parseCST(Node node, ParseContext context) {
        var CST = new SchemaNode(node);
        if (node == null) {
            return ParseResult.parsingFailed(new ArrayList<>());
        }
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
}
