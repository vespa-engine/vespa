package ai.vespa.schemals.schemadocument;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentItem;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.common.StringUtils;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;

/**
 * Class responsible for maintaining the set of open documents and reparsing them.
 * When {@link SchemaDocumentScheduler#updateFile} is called, it will call {@link DocumentManager#updateFileContent} on the appropriate file 
 * and also other files that may have dependencies on the contents of the file.
 */
public class SchemaDocumentScheduler {

    private ClientLogger logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;
    private SchemaMessageHandler messageHandler;
    private Map<String, DocumentManager> workspaceFiles = new HashMap<>();
    private boolean reparseDescendants = true;
    private URI workspaceURI = null;

    public SchemaDocumentScheduler(ClientLogger logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, SchemaMessageHandler messageHandler) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.messageHandler = messageHandler;
    }

    public void updateFile(String fileURI, String content) {
        updateFile(fileURI, content, null);
    }

    public void updateFile(String fileURI, String content, Integer version) {
        boolean isSchemaFile = fileURI.toLowerCase().endsWith(".sd");
        if (!workspaceFiles.containsKey(fileURI)) {
            if (isSchemaFile) {
                workspaceFiles.put(fileURI, new SchemaDocument(logger, diagnosticsHandler, schemaIndex, this, fileURI));
            } else {
                workspaceFiles.put(fileURI, new RankProfileDocument(logger, diagnosticsHandler, schemaIndex, this, fileURI));
            }
        }

        // TODO: a lot of parsing going on. It mostly should be reference resolving, not necessarily reparsing of entire contents.
        workspaceFiles.get(fileURI).updateFileContent(content, version);
        boolean needsReparse = false;

        if (isSchemaFile && reparseDescendants) {
            Set<String> parsedURIs = new HashSet<>() {{ add(fileURI); }};
            for (String descendantURI : schemaIndex.getDocumentInheritanceGraph().getAllDescendants(fileURI)) {
                if (descendantURI.equals(fileURI)) continue;

                if (workspaceFiles.containsKey(descendantURI)) {
                    workspaceFiles.get(descendantURI).reparseContent();
                    parsedURIs.add(descendantURI);
                }
            }

            // Reparse documents that holds references to this document
            String schemaIdentifier = ((SchemaDocument)workspaceFiles.get(fileURI)).getSchemaIdentifier();
            Optional<Symbol> documentDefinition = schemaIndex.findSymbol(null, SymbolType.DOCUMENT, schemaIdentifier);

            if (documentDefinition.isPresent()) {
                for (Symbol referencesThisDocument : schemaIndex.getDocumentReferenceGraph().getAllDescendants(documentDefinition.get())) {
                    String descendantURI = referencesThisDocument.getFileURI();
                    if (!parsedURIs.contains(descendantURI) && workspaceFiles.containsKey(descendantURI)) {
                        workspaceFiles.get(referencesThisDocument.getFileURI()).reparseContent();
                        parsedURIs.add(descendantURI);
                    }
                }
            }

            // reparse rank profile files belonging to this document
            for (var entry : workspaceFiles.entrySet()) {
                if ((entry.getValue() instanceof RankProfileDocument)) {
                    RankProfileDocument document  = (RankProfileDocument)entry.getValue();
                    if (document.schemaSymbol().isPresent() && document.schemaSymbol().get().fileURIEquals(fileURI)) {
                        entry.getValue().reparseContent();
                        needsReparse = true;
                    }
                }
            }
        }

        if (needsReparse) {
            workspaceFiles.get(fileURI).reparseContent();
        }
    }

    public String getWorkspaceURI() {
        return this.workspaceURI.toString();
    }

    public void openDocument(TextDocumentItem document) {
        logger.info("Opening document: " + document.getUri());

        if (workspaceURI == null) {
            Optional<URI> workspaceURI = FileUtils.findSchemaDirectory(URI.create(document.getUri()));
            if (workspaceURI.isEmpty()) {
                messageHandler.sendMessage(MessageType.Warning, 
                    "The file " + document.getUri() + 
                    " does not appear to be inside a 'schemas' directory. " + 
                    "Language support will be limited.");
            } else {
                setupWorkspace(workspaceURI.get());
            }
        }

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
            logger.info("Adding document: " + fileURI);
            String content = FileUtils.readFromURI(fileURI);
            updateFile(fileURI, content);
        } catch(IOException ex) {
            this.logger.error("Failed to read file: " + fileURI);
        }
    }

    public void closeDocument(String fileURI) {
        logger.info("Closing document: " + fileURI);
        workspaceFiles.get(fileURI).setIsOpen(false);

        File file = new File(URI.create(fileURI));
        if (!file.exists()) {
            cleanUpDocument(fileURI);
        }
    }

    private void cleanUpDocument(String fileURI) {
        logger.info("Removing document: "+ fileURI);

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

    public RankProfileDocument getRankProfileDocument(String fileURI) {
        DocumentManager genericDocument = getDocument(fileURI);
        if (!(genericDocument instanceof RankProfileDocument)) return null;
        return (RankProfileDocument)genericDocument;
    }

    public DocumentManager getDocument(String fileURI) {
        return workspaceFiles.get(fileURI);
    }

    public boolean fileURIExists(String fileURI) {
        return workspaceFiles.containsKey(fileURI);
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


    public void setupWorkspace(URI workspaceURI) {
        this.workspaceURI = workspaceURI;

        //messageHandler.messageTrace("Scanning workspace: " + workspaceURI.toString());
        messageHandler.logMessage(MessageType.Info, "Scanning workspace: " + workspaceURI.toString());

        setReparseDescendants(false);
        for (String fileURI : FileUtils.findSchemaFiles(workspaceURI.toString(), this.logger)) {
            //messageHandler.messageTrace("Parsing file: " + fileURI);
            messageHandler.logMessage(MessageType.Info, "Parsing file: " + fileURI);
            addDocument(fileURI);
        }

        for (String fileURI : FileUtils.findRankProfileFiles(workspaceURI.toString(), this.logger)) {
            //messageHandler.messageTrace("Parsing file: " + fileURI);
            messageHandler.logMessage(MessageType.Info, "Parsing file: " + fileURI);
            addDocument(fileURI);
        }
        reparseInInheritanceOrder();
        setReparseDescendants(true);
    }
}
