package ai.vespa.schemals.parser;

import java.io.PrintStream;
import java.util.HashMap;

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
}
