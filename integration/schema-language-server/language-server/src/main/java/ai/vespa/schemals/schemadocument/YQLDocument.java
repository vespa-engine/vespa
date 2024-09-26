package ai.vespa.schemals.schemadocument;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.parser.yqlplus.Node;
import ai.vespa.schemals.parser.yqlplus.ParseException;
import ai.vespa.schemals.parser.yqlplus.YQLPlusParser;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.YQLNode;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class YQLDocument implements DocumentManager {

    record ParseResult(List<Diagnostic> diagnostics, Optional<YQLNode> CST) {}

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

    private ParseContext getParseContext() {
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

    private static ParseResult parseYQL(String content, ClientLogger logger) {
        CharSequence charSequence = content.toLowerCase();
        YQLPlusParser parser = new YQLPlusParser(charSequence);

        try {
            parser.statement();
        } catch (ParseException exception) {
            // Ignored, marked by dirty node
        }

        Node node = parser.rootNode();
        YQLNode retNode = new YQLNode(node);
        YQLUtils.printTree(logger, node);

        return new ParseResult(List.of(), Optional.of(retNode));
    }

    public static ParseResult parseContent(ParseContext context) {
        YQLNode ret = new YQLNode(StringUtils.getStringRange(context.content()));

        int pipeIndex = context.content().indexOf('|', 0);
        String YQLString = pipeIndex == -1 ? context.content() : context.content().substring(0, pipeIndex);
        ParseResult YQLResult = parseYQL(YQLString, context.logger());

        if (YQLResult.CST.isEmpty()) return YQLResult;

        ret.addChild(YQLResult.CST.get());

        ArrayList<Diagnostic> diagnostics = new ArrayList<>(YQLResult.diagnostics());

        if (pipeIndex != -1 && pipeIndex + 1 < context.content().length()) {
            String groupingString = context.content().substring(pipeIndex + 1);
            ParseResult groupingResult = VespaGroupingParser.parseVespaGrouping(groupingString, context.logger());
            if (groupingResult.CST.isPresent()) {
                ret.addChild(groupingResult.CST.get());
            }

            for (Diagnostic diagnostic : groupingResult.diagnostics) {
                diagnostics.add(diagnostic); // TODO: Add offset to the positions
            }
        }

        traverseCST(ret, context, diagnostics);

        return new ParseResult(diagnostics, Optional.of(ret));
    }

    private static void traverseCST(YQLNode node, ParseContext context, ArrayList<Diagnostic> diagnostics) {

        for (Identifier<YQLNode> identifier : context.YQLIdentifiers()) {
            diagnostics.addAll(identifier.identify(node));
        }

        for (YQLNode child : node) {
            traverseCST(child, context, diagnostics);
        }
    }

    @Override
    public void reparseContent() {
        if (this.fileContent != null) {
            this.updateFileContent(this.fileContent);
        }
    }

    @Override
    public boolean setIsOpen(boolean isOpen) {
        this.isOpen = isOpen;
        return isOpen;
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
        return new SchemaDocumentLexer();
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
