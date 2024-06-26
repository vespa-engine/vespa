package ai.vespa.schemals.parser;

import java.io.PrintStream;
import java.util.HashMap;

import org.eclipse.lsp4j.TextDocumentItem;

import ai.vespa.schemals.SchemaDiagnosticsHandler;

public class SchemaDocumentScheduler {

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private HashMap<String, SchemaDocumentParser> openDocuments;

    public SchemaDocumentScheduler(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        openDocuments = new HashMap<String, SchemaDocumentParser>();
    }

    public void updateFile(String fileURI, String content) {
        if (openDocuments.containsKey(fileURI)) {
            openDocuments.get(fileURI).updateFileContent(content);
            return;
        }

        openDocuments.put(fileURI, new SchemaDocumentParser(logger, diagnosticsHandler, fileURI, content));
    }

    public void openDocument(TextDocumentItem document) {
        updateFile(document.getUri(), document.getText());
    }

    public void closeDocument(String fileURI) {
        logger.println("Closing document: " + fileURI);
        openDocuments.remove(fileURI);
    }

    public SchemaDocumentParser getDocument(String fileURI) {
        return openDocuments.get(fileURI);
    }
}
