package ai.vespa.schemals.schemadocument;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.HashMap;

import org.eclipse.lsp4j.TextDocumentItem;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.index.SchemaIndex;

public class SchemaDocumentScheduler {

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;
    private HashMap<String, DocumentManager> workspaceFiles = new HashMap<>();
    private boolean reparseDescendants = true;

    public SchemaDocumentScheduler(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
    }

    public void updateFile(String fileURI, String content) {
        updateFile(fileURI, content, null);
    }

    public void updateFile(String fileURI, String content, Integer version) {
        boolean isSchemaFile = fileURI.toLowerCase().endsWith(".sd");
        if (!workspaceFiles.containsKey(fileURI)) {
            if (isSchemaFile) {
                workspaceFiles.put(fileURI, new SchemaDocument(logger, diagnosticsHandler, schemaIndex, fileURI));
            } else {
                workspaceFiles.put(fileURI, new RankProfileDocument(logger, diagnosticsHandler, schemaIndex, fileURI));
            }
        }

        workspaceFiles.get(fileURI).updateFileContent(content, version);

        if (isSchemaFile && reparseDescendants) {
            for (String descendantURI : schemaIndex.getDocumentInheritanceGraph().getAllDescendants(fileURI)) {
                if (workspaceFiles.containsKey(descendantURI)) {
                    workspaceFiles.get(descendantURI).reparseContent();
                }
            }
        }
    }

    public void openDocument(TextDocumentItem document) {
        logger.println("Opening document: " + document.getUri());

        updateFile(document.getUri(), document.getText(), document.getVersion());
        workspaceFiles.get(document.getUri()).setIsOpen(true);
    }

    /*
     * Will read the file from disk if not already opened.
     * Does nothing if the document is already open (and thus managed by the client)
     */
    public void addDocument(String fileURI) {
        if (workspaceFiles.containsKey(fileURI)) return;

        try {
            logger.println("Adding document: " + fileURI);
            String content = FileUtils.readFromURI(fileURI);
            updateFile(fileURI, content);
        } catch(IOException ex) {
            this.logger.println("Failed to read file: " + fileURI);
        }
    }

    public void closeDocument(String fileURI) {
        logger.println("Closing document: " + fileURI);
        workspaceFiles.get(fileURI).setIsOpen(false);

        File file = new File(URI.create(fileURI));
        if (!file.exists()) {
            cleanUpDocument(fileURI);
        }
    }

    private void cleanUpDocument(String fileURI) {
        logger.println("Removing document: "+ fileURI);

        schemaIndex.clearDocument(fileURI);
        workspaceFiles.remove(fileURI);
    }

    public boolean removeDocument(String fileURI) {
        boolean wasOpen = workspaceFiles.get(fileURI).getIsOpen();
        closeDocument(fileURI);
        cleanUpDocument(fileURI);
        return wasOpen;
    }

    public SchemaDocument getSchemaDocument(String fileURI) {
        DocumentManager genericDocument = getDocument(fileURI);
        if (!(genericDocument instanceof SchemaDocument)) return null;
        return (SchemaDocument)genericDocument;
    }

    public DocumentManager getDocument(String fileURI) {
        return workspaceFiles.get(fileURI);
    }

    public void reparseInInheritanceOrder() {
        for (String fileURI : schemaIndex.getDocumentInheritanceGraph().getTopoOrdering()) {
            if (workspaceFiles.containsKey(fileURI)) {
                workspaceFiles.get(fileURI).reparseContent();
            }
        }
    }

    public void setReparseDescendants(boolean reparseDescendants) {
        this.reparseDescendants = reparseDescendants;
    }

    public boolean fileURIExists(String fileURI) {
        return workspaceFiles.containsKey(fileURI);
    }

}
