package ai.vespa.schemals.schemadocument;

import ai.vespa.schemals.tree.SchemaNode;

/**
 * FileManager
 * For each file the language server is responsible for there will be 
 * a corresponding class implementing FileManager which is responsible for parsing the file and getting diagnostics
 */
public interface DocumentManager {

    public void updateFileContent(String content);
    public void updateFileContent(String content, Integer version);

    public void reparseContent();

    public boolean setIsOpen(boolean isOpen);
    public boolean getIsOpen();

    public SchemaNode getRootNode();

    public String getFileURI();

    public String getCurrentContent();
}
