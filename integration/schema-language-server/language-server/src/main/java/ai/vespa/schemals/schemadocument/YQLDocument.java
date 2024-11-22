package ai.vespa.schemals.schemadocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.parser.yqlplus.ParseException;
import ai.vespa.schemals.parser.yqlplus.YQLPlusParser;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.YQLNode;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class YQLDocument implements DocumentManager {

    public static class ParseResult {
        public List<Diagnostic> diagnostics;
        public Optional<YQLNode> CST;

        ParseResult(List<Diagnostic> diagnostics, Optional<YQLNode> CST) {
            this.diagnostics = diagnostics;
            this.CST = CST;
        }

        public List<Diagnostic> diagnostics() { return this.diagnostics; }
        public Optional<YQLNode> CST() { return this.CST; }
    }

    static class YQLPartParseResult extends ParseResult {
        public int charsRead;

        YQLPartParseResult(List<Diagnostic> diagnostics, Optional<YQLNode> CST, int charsRead) {
            super(diagnostics, CST);
            this.charsRead = charsRead;
        }

        public int charsRead() { return this.charsRead; }
    }

    boolean isOpen = false;
    String fileURI;
    String fileContent = "";

    ClientLogger logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;
    private SchemaDocumentScheduler scheduler;

    private YQLNode CST;

    YQLDocument(ClientLogger logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, SchemaDocumentScheduler scheduler, String fileURI) {
        this.fileURI = fileURI;
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.scheduler = scheduler;
    }

    @Override
    public ParseContext getParseContext() {
        ParseContext context = new ParseContext(fileContent, logger, fileURI, schemaIndex, scheduler);
        context.useVespaGroupingIdentifiers();
        return context;
    }

    @Override
    public void updateFileContent(String content) {
        fileContent = content;

        ParseContext context = getParseContext();

        ParseResult parseResult = parseContent(context);

        diagnosticsHandler.publishDiagnostics(fileURI, parseResult.diagnostics());

        if (parseResult.CST.isPresent()) {
            CST = parseResult.CST.get();
        }
    }

    @Override
    public void updateFileContent(String content, Integer version) {
        updateFileContent(content);
    }

    private static YQLPartParseResult parseYQLPart(CharSequence content, ClientLogger logger, Position offset) {
        // CharSequence charSequence = content.toLowerCase();
        YQLPlusParser parser = new YQLPlusParser(content);

        try {
            parser.statement();
        } catch (ParseException exception) {
            // Ignored, marked by dirty node
        }

        int charsRead = parser.getToken(0).getEndOffset();

        if (charsRead == 0) return new YQLPartParseResult(List.of(), Optional.empty(), charsRead);

        ai.vespa.schemals.parser.yqlplus.Node node = parser.rootNode();
        YQLNode retNode = new YQLNode(node, offset);
        // YQLUtils.printTree(logger, node);

        return new YQLPartParseResult(List.of(), Optional.of(retNode), charsRead);
    }

    private static boolean detectContinuation(String inputString) {
        for (int i = 0; i < inputString.length(); i++) {
            if (inputString.charAt(i) != ' ') {
                return inputString.charAt(i) == '{';
            }
        }
        return false;
    }

    private static YQLPartParseResult parseContinuation(String inputString, Position offset) {

        YQLPlusParser parser = new YQLPlusParser(inputString);

        try {
            parser.map_expression();
        } catch (ParseException exception) {
            // Ignored, marked as dirty node
        }

        var node = parser.rootNode();
        YQLNode retNode = new YQLNode(node, offset);

        int charsRead = parser.getToken(0).getEndOffset();

        return new YQLPartParseResult(List.of(), Optional.of(retNode), charsRead);
    }

    private static YQLPartParseResult parseYQLQuery(ParseContext context, String queryString, Position offset) {
        YQLNode ret = new YQLNode(new Range(offset, offset));

        int pipeIndex = queryString.indexOf('|');
        String YQLString = pipeIndex == -1 ? queryString : queryString.substring(0, pipeIndex);
        YQLPartParseResult YQLResult = parseYQLPart(YQLString, context.logger(), offset);

        if (YQLResult.CST.isEmpty()) return YQLResult;

        ret.addChild(YQLResult.CST.get());

        ArrayList<Diagnostic> diagnostics = new ArrayList<>(YQLResult.diagnostics());

        int charsRead = YQLResult.charsRead();

        if (pipeIndex != -1 && pipeIndex + 1 < queryString.length()) {
            String charsBeforePipe = queryString.substring(charsRead, pipeIndex);
            if (charsBeforePipe.strip().length() == 0) {
                String groupingString = queryString.substring(pipeIndex + 1); // Do not include pipe char
                Position YQLStringPosition = StringUtils.getStringPosition(YQLString);
                Position groupOffsetWithoutPipe = CSTUtils.addPositions(offset, YQLStringPosition);

                Position groupOffset = CSTUtils.addPositions(groupOffsetWithoutPipe, new Position(0, 1)); // Add pipe char

                ret.addChild(new YQLNode(new Range(groupOffsetWithoutPipe, groupOffset), "|"));
                charsRead++;

                // Look for continuation
                boolean continuationDetected = detectContinuation(groupingString);
                if (continuationDetected) {
                    YQLPartParseResult continuationResults = parseContinuation(groupingString, groupOffset);

                    diagnostics.addAll(continuationResults.diagnostics());
                    if (continuationResults.CST().isPresent()) {
                        ret.addChild(continuationResults.CST().get());
                    }

                    charsRead += continuationResults.charsRead();
                    String continuationString = groupingString.substring(0, continuationResults.charsRead());
                    Position continuationPosition = StringUtils.getStringPosition(continuationString);

                    groupingString = groupingString.substring(continuationResults.charsRead());
                    groupOffset = CSTUtils.addPositions(groupOffset, continuationPosition);
                }

                if (groupingString.length() > 0 && groupingString.strip().length() > 0) {
                    YQLPartParseResult groupingResult = VespaGroupingParser.parseVespaGrouping(groupingString, context.logger(), groupOffset);
                    if (groupingResult.CST.isPresent()) {
                        ret.addChild(groupingResult.CST.get());
                    }
        
                    diagnostics.addAll(groupingResult.diagnostics());
                    charsRead += groupingResult.charsRead(); // Add one for the pipe symbol
                }
    
            }

        }

        Range newRange = StringUtils.getStringRange(queryString.substring(0, charsRead));
        ret.setRange(CSTUtils.addPositionToRange(offset, newRange));

        traverseCST(ret, context, diagnostics);

        return new YQLPartParseResult(diagnostics, Optional.of(ret), charsRead);
    }

    public static ParseResult parseContent(ParseContext context) {
        String content = context.content();
        YQLNode ret = new YQLNode(StringUtils.getStringRange(content));
        ArrayList<Diagnostic> diagnostics = new ArrayList<>();

        int charsRead = 0;
        int linesRead = 0;

        while (charsRead < content.length()) {

            String toParser = content.substring(charsRead);
            if (toParser.strip().length() == 0) {
                break;
            }

            YQLPartParseResult result = parseYQLQuery(context, toParser, new Position(linesRead, 0));
            diagnostics.addAll(result.diagnostics());
    
            if (result.CST().isPresent()) {
                ret.addChild(result.CST().get());
            }

            if (result.charsRead() == 0) result.charsRead++;
            
            int newOffset = content.indexOf('\n', charsRead + result.charsRead());
            if (newOffset == -1) {
                newOffset = content.length();
            }
            String substr = content.substring(charsRead, newOffset);
            linesRead += StringUtils.countNewLines(substr);
            charsRead = newOffset;
        }

        YQLUtils.printTree(context.logger(), ret);

        return new ParseResult(diagnostics, Optional.of(ret));
    }

    private static void traverseCST(YQLNode node, ParseContext context, ArrayList<Diagnostic> diagnostics) {

        for (Identifier<YQLNode> identifier : context.YQLIdentifiers()) {
            diagnostics.addAll(identifier.identify(node));
        }

        for (Node child : node) {
            traverseCST(child.getYQLNode(), context, diagnostics);
        }
    }

    @Override
    public void reparseContent() {
        if (this.fileContent != null) {
            this.updateFileContent(this.fileContent);
        }
    }

    @Override
    public void setIsOpen(boolean isOpen) {
        this.isOpen = isOpen;
    }

    @Override
    public boolean getIsOpen() { return isOpen; }

    @Override
    public SchemaNode getRootNode() {
        return null;
    }

    @Override
    public YQLNode getRootYQLNode() {
        return CST;
    }

    @Override
    public SchemaDocumentLexer lexer() {
        throw new UnsupportedOperationException("Getting the lexer from a YQLDocument is not implemented");
    }

    @Override
    public String getFileURI() {
        return fileURI;
    }

    @Override
    public String getCurrentContent() {
        return fileContent;
    }

    @Override
    public VersionedTextDocumentIdentifier getVersionedTextDocumentIdentifier() {
        return new VersionedTextDocumentIdentifier(fileURI, 0);
    }

    @Override
    public DocumentType getDocumentType() { return DocumentType.YQL; }

}
