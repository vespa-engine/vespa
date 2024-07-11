package ai.vespa.schemals.context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.util.HashMap;

import org.eclipse.lsp4j.TextDocumentItem;

import com.yahoo.io.IOUtils;
import com.yahoo.io.reader.NamedReader;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.index.SchemaIndex;

public class SchemaDocumentScheduler {

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;
    private HashMap<String, SchemaDocumentParser> workspaceDocuments = new HashMap<String, SchemaDocumentParser>();
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
        if (!workspaceDocuments.containsKey(fileURI)) {
            workspaceDocuments.put(fileURI, new SchemaDocumentParser(logger, diagnosticsHandler, schemaIndex, fileURI, content, version));
        } else {
            workspaceDocuments.get(fileURI).updateFileContent(content, version);
        }

        if (reparseDescendants) {
            for (String descendantURI : schemaIndex.getAllDocumentDescendants(fileURI)) {
                if (workspaceDocuments.containsKey(descendantURI)) {
                    workspaceDocuments.get(descendantURI).reparseContent();
                }
            }
        }
    }

    public void openDocument(TextDocumentItem document) {
        logger.println("Opening document: " + document.getUri());
        
        updateFile(document.getUri(), document.getText(), document.getVersion());
        workspaceDocuments.get(document.getUri()).setIsOpen(true);
    }

    /*
     * Will read the file from disk if not already opened.
     * Does nothing if the document is already open (and thus managed by the client)
     */
    public void addDocument(String fileURI) {
        if (workspaceDocuments.containsKey(fileURI)) return;

        try {
            logger.println("Adding document: " + fileURI);
            String content = readFromURI(fileURI);
            updateFile(fileURI, content);
        } catch(IOException ex) {
            this.logger.println("Failed to read file: " + fileURI);
        }

    }

    public void closeDocument(String fileURI) {
        logger.println("Closing document: " + fileURI);
        workspaceDocuments.get(fileURI).setIsOpen(false);

        File file = new File(URI.create(fileURI));
        if (!file.exists()) {
            cleanUpDocumnet(fileURI);
        }
    }

    private void cleanUpDocumnet(String fileURI) {
        logger.println("Removing document: "+ fileURI);

        schemaIndex.clearDocument(fileURI);
        workspaceDocuments.remove(fileURI);
    }

    public boolean removeDocument(String fileURI) {
        boolean wasOpen = workspaceDocuments.get(fileURI).getIsOpen();
        closeDocument(fileURI);
        cleanUpDocumnet(fileURI);
        return wasOpen;
    }

    public SchemaDocumentParser getDocument(String fileURI) {
        return workspaceDocuments.get(fileURI);
    }

    public void reparseInInheritanceOrder() {
        for (String fileURI : schemaIndex.getAllDocumentsInInheritanceOrder()) {
            if (workspaceDocuments.containsKey(fileURI)) {
                workspaceDocuments.get(fileURI).reparseContent();
            }
        }
    }

    public void setReparseDescendants(boolean reparseDescendants) {
        this.reparseDescendants = reparseDescendants;
    }

    private String readFromURI(String fileURI) throws IOException {
        File file = new File(URI.create(fileURI));
        return IOUtils.readAll(new FileReader(file));
    }
}
