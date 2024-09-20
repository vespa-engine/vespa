package ai.vespa.schemals.schemadocument;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.YQLNode;

public class ServicesXMLDocument implements DocumentManager {

    private ClientLogger logger;
    private String fileURI;
    private boolean isOpen;
    private String content;
    private Integer currentVersion;
    private SchemaDiagnosticsHandler diagnosticsHandler;

    public ServicesXMLDocument(ClientLogger logger, SchemaDiagnosticsHandler diagnosticsHandler, String fileURI) {
        this.logger = logger;
        this.fileURI = fileURI;
        this.isOpen = false;
        this.currentVersion = 0;
        this.diagnosticsHandler = diagnosticsHandler;
    }

    @Override
    public void updateFileContent(String content) {
        // TODO Auto-generated method stub
        this.content = content;

        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            InputSource inputSource = new InputSource(new StringReader(content));
            Document doc = builder.parse(inputSource);
            this.logger.info("Successfully parsed XML document.");
        } catch (Exception ex) {
            this.logger.error("Error when parsing XML document [" + ex.getClass().toString() + "]: " + ex.getMessage());
        }
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

    @Override
    public YQLNode getRootYQLNode() {
        return null;
    }
}
