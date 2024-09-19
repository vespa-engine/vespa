package ai.vespa.schemals.schemadocument;

import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.parser.yqlplus.Node;
import ai.vespa.schemals.parser.yqlplus.ParseException;
import ai.vespa.schemals.parser.yqlplus.YQLPlusParser;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class YQLDocument implements DocumentManager {

    boolean isOpen = false;
    String fileURI;
    String fileContent = "";

    ClientLogger logger;
    SchemaDiagnosticsHandler diagnosticsHandler;

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
        YQLUtils.printTree(logger, node);

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
    };

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
