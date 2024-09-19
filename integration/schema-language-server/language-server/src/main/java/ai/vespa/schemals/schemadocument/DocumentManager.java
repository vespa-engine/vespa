package ai.vespa.schemals.schemadocument;

import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;

import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.YQLNode;

/**
 * DocumentManager
 * For each file the language server is responsible for there will be a corresponding class implementing DocumentManager which is responsible for parsing the file and getting diagnostics.
 */
public interface DocumentManager {

    public enum DocumentType {
        SCHEMA,
        PROFILE,
        YQL,
        SERVICESXML
    }

    public void updateFileContent(String content);
    public void updateFileContent(String content, Integer version);

    public void reparseContent();

    public void setIsOpen(boolean isOpen);
    public boolean getIsOpen();

    public SchemaNode getRootNode();
    public YQLNode getRootYQLNode();

    public SchemaDocumentLexer lexer();

    public String getFileURI();

    public String getCurrentContent();

    public VersionedTextDocumentIdentifier getVersionedTextDocumentIdentifier();

    public DocumentType getDocumentType();
}
