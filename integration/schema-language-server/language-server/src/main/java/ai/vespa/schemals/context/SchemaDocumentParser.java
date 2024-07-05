package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;


import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.context.parser.Identifier;
import ai.vespa.schemals.context.parser.IdentifyDeprecatedToken;
import ai.vespa.schemals.context.parser.IdentifyDirtyNodes;
import ai.vespa.schemals.context.parser.IdentifySymbolDefinition;
import ai.vespa.schemals.context.parser.IdentifySymbolReferences;
import ai.vespa.schemals.context.parser.IdentifyType;
import ai.vespa.schemals.parser.SchemaParser;
import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token;

import ai.vespa.schemals.parser.indexinglanguage.IndexingParser;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParser;

import com.yahoo.schema.parser.ParsedSchema;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.functions.Diag;
import com.yahoo.schema.parser.ParsedBlock;

import com.yahoo.vespa.indexinglanguage.expressions.Expression;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.indexinglanguage.ILUtils;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;

public class SchemaDocumentParser {

    public record ParseContext(String content, PrintStream logger, String fileURI, List<Identifier> identifiers) { }

    public record ParseResult(ArrayList<Diagnostic> diagnostics, Optional<SchemaNode> CST) {
        public static ParseResult parsingFailed(ArrayList<Diagnostic> diagnostics) {
            return new ParseResult(diagnostics, Optional.empty());
        }
    }

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;

    private List<Identifier> parseIdentifiers;

    private String fileURI = "";
    private String content = "";
    
    private SchemaNode CST;

    public SchemaDocumentLexer lexer = new SchemaDocumentLexer();

    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI) {
        this(logger, diagnosticsHandler, schemaIndex, fileURI, null);
    }
    
    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI, String content) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.fileURI = fileURI;

        this.parseIdentifiers = constructIdentifiers(logger, fileURI, schemaIndex);
        if (content != null) {
            updateFileContent(content);
        };
    }

    public static List<Identifier> constructIdentifiers(PrintStream logger, String fileURI, SchemaIndex schemaIndex) {
        return new ArrayList<Identifier>() {{
            add(new IdentifySymbolDefinition(logger, fileURI, schemaIndex));
            add(new IdentifyType(logger));
            add(new IdentifySymbolReferences(logger, fileURI, schemaIndex));
            add(new IdentifyDeprecatedToken(logger));
            add(new IdentifyDirtyNodes(logger));
        }};
    }

    public ParseContext getParseContext(String content) {
        return new ParseContext(content, this.logger, this.fileURI, this.parseIdentifiers);
    }

    public void updateFileContent(String content) {
        this.content = content;
        schemaIndex.clearDocument(fileURI);

        var parsingResult = parseContent(getParseContext(content));

        diagnosticsHandler.publishDiagnostics(fileURI, parsingResult.diagnostics());

        logger.println("Found " + parsingResult.diagnostics().size() + " errors during parsing");

        if (parsingResult.CST().isPresent()) {
            this.CST = parsingResult.CST().get();
            lexer.setCST(CST);
        }

        //CSTUtils.printTree(logger, CST);
    }

    public String getFileURI() {
        return fileURI;
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


    private SchemaNode getNodeAtPosition(SchemaNode node, Position pos, boolean onlyLeaf, boolean findNearest) {
        if (node.isLeaf() && CSTUtils.positionInRange(node.getRange(), pos)) {
            return node;
        }

        if (!CSTUtils.positionInRange(node.getRange(), pos)) {
            if (findNearest && !onlyLeaf)return node;
            return null;
        }

        for (int i = 0; i < node.size(); i++) {
            SchemaNode child = node.get(i);

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

        ArrayList<Diagnostic> errors = new ArrayList<Diagnostic>();

        try {

            parserStrict.Root();
        } catch (ParseException e) {

            Node.TerminalNode node = e.getToken();

            Range range = CSTUtils.getNodeRange(node);
            String message = e.getMessage();

            
            Throwable cause = e.getCause();
            if (
                cause != null &&
                cause instanceof com.yahoo.schema.parser.ParseException
            ) {
                context.logger().println(e);
                context.logger().println(cause);
            }

            errors.add(new Diagnostic(range, message));


        } catch (IllegalArgumentException e) {
            // Complex error, invalidate the whole document

            errors.add(
                new Diagnostic(
                    new Range(
                        new Position(0, 0),
                        new Position((int)context.content().lines().count() - 1, 0)
                    ),
                    e.getMessage())
                );
            return ParseResult.parsingFailed(errors);
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
        errors.addAll(tolerantResult.diagnostics());

        // CSTUtils.printTree(logger, CST);

        return new ParseResult(errors, tolerantResult.CST());
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

        for (int i = 0; i < node.size(); i++) {
            ret.addAll(traverseCST(node.get(i), context));
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
            context.logger().println("Trying to parse indexing script: " + script);
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
