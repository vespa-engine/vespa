package ai.vespa.schemals.schemadocument;

import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.yqlplus.Node;
import ai.vespa.schemals.parser.yqlplus.ParseException;
import ai.vespa.schemals.parser.yqlplus.YQLPlusParser;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.YQLNode;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class YQLDocument implements DocumentManager {

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
    public ParseContext getParseContext() {
        return null;
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

    @Override
    public void reparseContent() {
        CharSequence charSequence = fileContent.toLowerCase();
        YQLPlusParser parser = new YQLPlusParser(charSequence);

        try {
            parser.statement();
    
        } catch (ParseException exception) {
            logger.error(exception.getMessage());
        }

        Node node = parser.rootNode();
        CST = new YQLNode(node);
        YQLUtils.printTree(logger, node);

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
