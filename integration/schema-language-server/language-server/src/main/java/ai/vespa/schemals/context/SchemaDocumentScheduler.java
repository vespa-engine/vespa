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
    private HashMap<String, SchemaDocumentParser> openDocuments = new HashMap<String, SchemaDocumentParser>();

    public SchemaDocumentScheduler(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
    }

    public void updateFile(String fileURI, String content) {
        if (!openDocuments.containsKey(fileURI)) {
            openDocuments.put(fileURI, new SchemaDocumentParser(logger, diagnosticsHandler, schemaIndex, fileURI, content));
        } else {
            openDocuments.get(fileURI).updateFileContent(content);
        }


        logger.println(fileURI + " changed, Updating descendants:");
        for (String descendantURI : schemaIndex.getAllDocumentDescendants(fileURI)) {
            logger.println("    " + descendantURI);
            if (openDocuments.containsKey(descendantURI)) {
                openDocuments.get(descendantURI).reparseContent();
            }
        }
    }

    public void openDocument(TextDocumentItem document) {
        logger.println("Opening document: " + document.getUri());
        updateFile(document.getUri(), document.getText());
    }

    /*
     * Will read the file from disk if not already opened.
     * Does nothing if the document is already open (and thus managed by the client)
     */
    public void openDocument(String fileURI) {
        if (openDocuments.containsKey(fileURI)) return;

        try {
            logger.println("Opening document: " + fileURI);
            String content = readFromURI(fileURI);
            updateFile(fileURI, content);
        } catch(IOException ex) {
            this.logger.println("Failed to read file: " + fileURI);
        }
    }

    public void closeDocument(String fileURI) {
        logger.println("Closing document: " + fileURI);
        openDocuments.remove(fileURI);
    }

    public SchemaDocumentParser getDocument(String fileURI) {
        return openDocuments.get(fileURI);
    }

    private String readFromURI(String fileURI) throws IOException {
        File file = new File(URI.create(fileURI));
        return IOUtils.readAll(new FileReader(file));
    }
}
