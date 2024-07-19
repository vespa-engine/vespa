package ai.vespa.schemals.schemadocument;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.SchemaDiagnosticsHandler;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.common.FileUtils;

import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.SchemaParser;
import ai.vespa.schemals.parser.SubLanguageData;
import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.Node;


import ai.vespa.schemals.parser.indexinglanguage.IndexingParser;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.schemadocument.resolvers.AnnotationReferenceResolver;
import ai.vespa.schemals.schemadocument.resolvers.DocumentReferenceResolver;
import ai.vespa.schemals.schemadocument.resolvers.InheritanceResolver;
import ai.vespa.schemals.schemadocument.resolvers.RankExpressionSymbolResolver;
import ai.vespa.schemals.schemadocument.resolvers.ResolverTraversal;
import ai.vespa.schemals.schemadocument.resolvers.StructFieldDefinitionResolver;
import ai.vespa.schemals.schemadocument.resolvers.SymbolReferenceResolver;
import ai.vespa.schemals.schemadocument.resolvers.TypeNodeResolver;
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
    private SchemaDocumentScheduler scheduler;

    private String fileURI = "";
    private Integer version;
    private boolean isOpen = false;
    private String content = "";
    private String schemaDocumentIdentifier = null;
    
    private SchemaNode CST;

    public SchemaDocumentLexer lexer = new SchemaDocumentLexer();

    public SchemaDocument(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, SchemaDocumentScheduler scheduler, String fileURI) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.fileURI = fileURI;
        this.scheduler = scheduler;
    }
    
    public ParseContext getParseContext(String content) {
        ParseContext context = new ParseContext(content, this.logger, this.fileURI, this.schemaIndex, this.scheduler);
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

        logger.println("Parsing: " + fileURI);
        ParseContext context = getParseContext(content);
        var parsingResult = parseContent(context);

        parsingResult.diagnostics().addAll(verifyFileName());

        diagnosticsHandler.publishDiagnostics(fileURI, parsingResult.diagnostics());

        if (parsingResult.CST().isPresent()) {
            this.CST = parsingResult.CST().get();
            lexer.setCST(CST);
        }


        logger.println("======== CST for file: " + fileURI + " ========");
 
        CSTUtils.printTree(logger, CST);

        //schemaIndex.dumpIndex();

    }

    private List<Diagnostic> verifyFileName() {
        List<Diagnostic> ret = new ArrayList<>();

        List<Symbol> schemaSymbols = schemaIndex.getSymbolsByType(SymbolType.SCHEMA);

        Symbol schemaIdentifier = null;

        for (Symbol symbol : schemaSymbols) {
            if (symbol.getFileURI().equals(fileURI)) {
                schemaIdentifier = symbol;
                break;
            }
        }

        if (schemaIdentifier != null) {
            schemaDocumentIdentifier = schemaIdentifier.getShortIdentifier();

            if (!getFileName().equals(schemaDocumentIdentifier + ".sd")) {
                // TODO: quickfix
                ret.add(new Diagnostic(
                    schemaIdentifier.getNode().getRange(),
                    "Schema " + schemaDocumentIdentifier + " should be defined in a file with the name: " + schemaDocumentIdentifier + ".sd. File name is: " + getFileName(),
                    DiagnosticSeverity.Error,
                    ""
                ));
            }
        }

        return ret;
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

            diagnostics.add(new Diagnostic(range, message, DiagnosticSeverity.Error, ""));


        } catch (IllegalArgumentException e) {
            // Complex error, invalidate the whole document

            diagnostics.add(
                new Diagnostic(
                    new Range(
                        new Position(0, 0),
                        new Position((int)context.content().lines().count() - 1, 0)
                    ),
                    e.getMessage(),
                    DiagnosticSeverity.Error,
                    "")
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

        diagnostics.addAll(InheritanceResolver.resolveInheritances(context));

        for (SchemaNode typeNode : context.unresolvedTypeNodes()) {
            TypeNodeResolver.resolveType(context, typeNode.getSymbol());
        }
        
        context.clearUnresolvedTypeNodes();

        for (SchemaNode annotationReferenceNode : context.unresolvedAnnotationReferenceNodes()) {
            AnnotationReferenceResolver.resolveAnnotationReference(context, annotationReferenceNode.getSymbol());
        }

        context.clearUnresolvedAnnotationReferenceNodes();

        
        if (tolerantResult.CST().isPresent()) {

            diagnostics.addAll(RankExpressionSymbolResolver.resolveRankExpressionReferences(tolerantResult.CST().get(), context));

            diagnostics.addAll(StructFieldDefinitionResolver.resolve(context, tolerantResult.CST().get()));

            diagnostics.addAll(ResolverTraversal.traverse(context, tolerantResult.CST().get()));

            diagnostics.addAll(DocumentReferenceResolver.resolveDocumentReferences(context));
        }

        diagnostics.addAll(tolerantResult.diagnostics());

        return new ParseResult(diagnostics, tolerantResult.CST());
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
            var expression = parser.root();
            /*
            // TODO: Verify expression?
            try {
                var dataType = expression.verify();
                context.logger().println("ILSCRIPT GOOD: " + dataType.toString());
            } catch(Exception e) {
                context.logger().println("ILSCRIPT FAIL: " + e.getMessage());
            }
            */
            return new SchemaNode(parser.rootNode(), scriptStart);
        } catch(ai.vespa.schemals.parser.indexinglanguage.ParseException pe) {
            context.logger().println("Encountered parsing error in parsing feature list");
            Range range = ILUtils.getNodeRange(pe.getToken());
            range.setStart(CSTUtils.addPositions(scriptStart, range.getStart()));
            range.setEnd(CSTUtils.addPositions(scriptStart, range.getEnd()));

            diagnostics.add(new Diagnostic(range, pe.getMessage(), DiagnosticSeverity.Error, ""));
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
