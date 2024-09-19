package ai.vespa.schemals.schemadocument;

import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.tree.SchemaNode;

public class ServicesXMLDocument implements DocumentManager {

    private ClientLogger logger;
    private String fileURI;
    private boolean isOpen;
    private String content;
    private Integer currentVersion;

    public ServicesXMLDocument(ClientLogger logger, String fileURI) {
        this.logger = logger;
        this.fileURI = fileURI;
        this.isOpen = false;
        this.currentVersion = 0;
    }

    @Override
    public void updateFileContent(String content) {
        // TODO Auto-generated method stub
        this.content = content;
    }

    @Override
    public void updateFileContent(String content, Integer version) {
        // TODO Auto-generated method stub
        this.currentVersion = version;
        updateFileContent(content);
    }

    @Override
    public void reparseContent() {
        // TODO Auto-generated method stub
    }

    @Override
    public void setIsOpen(boolean isOpen) {
        this.isOpen = isOpen;
    }

    @Override
    public boolean getIsOpen() {
        return this.isOpen;
    }

    @Override
    public SchemaNode getRootNode() {
        return null;
    }

    @Override
    public SchemaDocumentLexer lexer() {
        return null;
    }

    @Override
    public String getFileURI() {
        return fileURI;
    }

    @Override
    public String getCurrentContent() {
        // TODO Auto-generated method stub
        return this.content;
    }

    @Override
    public VersionedTextDocumentIdentifier getVersionedTextDocumentIdentifier() {
        // TODO Auto-generated method stub
        return new VersionedTextDocumentIdentifier(fileURI, this.currentVersion);
    }

    @Override
    public DocumentType getDocumentType() {
        return DocumentType.SERVICESXML;
    }
}
