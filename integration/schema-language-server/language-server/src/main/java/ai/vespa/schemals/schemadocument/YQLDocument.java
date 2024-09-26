package ai.vespa.schemals.schemadocument;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.parser.yqlplus.Node;
import ai.vespa.schemals.parser.yqlplus.ParseException;
import ai.vespa.schemals.parser.yqlplus.YQLPlusParser;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.YQLNode;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class YQLDocument implements DocumentManager {

    record ParseResult(List<Diagnostic> diagnostics, Optional<YQLNode> CST) {}

    boolean isOpen = false;
    String fileURI;
    String fileContent = "";

    ClientLogger logger;
    SchemaDiagnosticsHandler diagnosticsHandler;

    private YQLNode CST;

    YQLDocument(ClientLogger logger, SchemaDiagnosticsHandler diagnosticsHandler, String fileURI) {
        this.fileURI = fileURI;
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
    }

    @Override
    public void updateFileContent(String content) {
        fileContent = content;

        reparseContent();
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
            logger.error(exception.getMessage());
        }

        Node node = parser.rootNode();
        YQLNode retNode = new YQLNode(node);
        YQLUtils.printTree(logger, node);

        return new ParseResult(List.of(), Optional.of(retNode));
    }

    @Override
    public void reparseContent() {

        YQLNode ret = new YQLNode(StringUtils.getStringRange(fileContent));

        int pipeIndex = fileContent.indexOf('|', 0);
        String YQLString = pipeIndex == -1 ? fileContent : fileContent.substring(0, pipeIndex);
        ParseResult YQLResult = parseYQL(YQLString, logger);

        if (YQLResult.CST.isEmpty()) return;

        ret.addChild(YQLResult.CST.get());

        if (pipeIndex != -1 && pipeIndex + 1 < fileContent.length()) {
            String groupingString = fileContent.substring(pipeIndex + 1);
            ParseResult groupingResult = VespaGroupingParser.parseVespaGrouping(groupingString, logger);
            if (groupingResult.CST.isPresent()) {
                ret.addChild(groupingResult.CST.get());
            }
        }

        CST = ret;
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
