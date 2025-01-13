package ai.vespa.schemals.schemadocument;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.TextDocumentItem;

import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.SchemaMessageHandler;
import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.common.SchemaDiagnostic.DiagnosticCode;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;

import ai.vespa.schemals.schemadocument.DocumentManager.DocumentType;
import ai.vespa.schemals.schemadocument.resolvers.SymbolReferenceResolver;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Class responsible for maintaining the set of open documents and reparsing them.
 * When {@link SchemaDocumentScheduler#updateFile} is called, it will call {@link DocumentManager#updateFileContent} on the appropriate file 
 * and also other files that may have dependencies on the contents of the file.
 */
public class SchemaDocumentScheduler {

    private Map<String, DocumentType> fileExtensions = new HashMap<>() {{
        put("sd", DocumentType.SCHEMA);
        put("profile", DocumentType.PROFILE);
        put("yql", DocumentType.YQL);
    }};

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

    private Optional<DocumentType> getDocumentTypeFromURI(String fileURI) {
        int dotIndex = fileURI.lastIndexOf('.');
        String fileExtension = fileURI.substring(dotIndex + 1).toLowerCase();

        DocumentType documentType = fileExtensions.get(fileExtension);
        if (documentType == null) return Optional.empty();
        return Optional.of(documentType);
    }

    public void updateFile(String fileURI, String content, Integer version) {
        logger.info("Updating file: " + fileURI);
        Optional<DocumentType> documentType = getDocumentTypeFromURI(fileURI);
        if (documentType.isEmpty()) return;

        if (!workspaceFiles.containsKey(fileURI)) {
            switch(documentType.get()) {
                case PROFILE:
                    workspaceFiles.put(fileURI, new RankProfileDocument(logger, diagnosticsHandler, schemaIndex, this, fileURI));
                    break;
                case SCHEMA:
                    workspaceFiles.put(fileURI, new SchemaDocument(logger, diagnosticsHandler, schemaIndex, this, fileURI));
                    break;
                case YQL:
                    workspaceFiles.put(fileURI, new YQLDocument(logger, diagnosticsHandler, schemaIndex, this, fileURI));
                    break;
            }
        }

        // TODO: a lot of parsing going on. It mostly should be reference resolving, not necessarily reparsing of entire contents.
        workspaceFiles.get(fileURI).updateFileContent(content, version);

        if (reparseDescendants) {
            Set<String> parsedURIs = new HashSet<>() {{ add(fileURI); }};
            if (documentType.get() == DocumentType.SCHEMA) {

                reparseSchemaFileDescendants(fileURI, parsedURIs);

            } else if (documentType.get() == DocumentType.PROFILE) {
                // Find the schema this rank profile belongs to and reparse from there
                RankProfileDocument document = getRankProfileDocument(fileURI);
                Optional<Symbol> schemaSymbol = document.schemaSymbol();

                if (schemaSymbol.isPresent()) {
                    reparseSchemaFileDescendants(schemaSymbol.get().getFileURI(), parsedURIs);
                }
            }
        }

        // Resolve remaining unresolved symbols after everything has parsed
        Map<String, List<Diagnostic>> undefinedSymbolDiagnostics = new HashMap<>();
        while (true) {
            boolean didResolve = false;
            for (var symbol : schemaIndex.getUnresolvedSymbols()) {
                String symbolURI = symbol.getFileURI();

                DocumentManager symbolDocument = getDocument(symbolURI);
                if (symbolDocument == null) continue; // bad situation

                if (!undefinedSymbolDiagnostics.containsKey(symbolURI))undefinedSymbolDiagnostics.put(symbolURI, new ArrayList<>());

                ParseContext context = symbolDocument.getParseContext();
                if (symbol.getNode() instanceof SchemaNode) {
                    List<Diagnostic> diagnostics = new ArrayList<>();
                    SymbolReferenceResolver.resolveSymbolReference(symbol.getNode().getSchemaNode(), context, diagnostics);
                    if (diagnostics.isEmpty()) {
                        didResolve = true;
                        break;
                    }
                }
            }
            if (!didResolve) break;
        }


        for (var symbol : schemaIndex.getUnresolvedSymbols()) {
            undefinedSymbolDiagnostics.get(symbol.getFileURI()).add(
            new SchemaDiagnostic.Builder()
                    .setRange( symbol.getNode().getRange())
                    .setMessage( "Undefined symbol " + symbol.getNode().getText())
                    .setSeverity(DiagnosticSeverity.Error)
                    .setCode(DiagnosticCode.UNDEFINED_SYMBOL)
                    .build()
            );
        }

        for (var entry : undefinedSymbolDiagnostics.entrySet()) {
            diagnosticsHandler.replaceUndefinedSymbolDiagnostics(entry.getKey(), entry.getValue());
        }
    }

    private void reparseSchemaFileDescendants(String fileURI, Set<String> parsedURIs) {
        for (String descendantURI : schemaIndex.getDocumentInheritanceGraph().getAllDescendants(fileURI)) {
            if (parsedURIs.contains(descendantURI)) continue;
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
            if (parsedURIs.contains(entry.getKey())) continue;
            if ((entry.getValue() instanceof RankProfileDocument)) {
                RankProfileDocument document  = (RankProfileDocument)entry.getValue();
                if (document.schemaSymbol().isPresent() && document.schemaSymbol().get().fileURIEquals(fileURI)) {
                    entry.getValue().reparseContent();
                    parsedURIs.add(entry.getKey());
                }
            }
        }
    }

    public String getWorkspaceURI() {
        if (this.workspaceURI == null) return null;
        return this.workspaceURI.toString();
    }

    public void openDocument(TextDocumentItem document) {
        String fileURI = FileUtils.decodeURL(document.getUri());
        logger.info("Opening document: " + fileURI);

        Optional<DocumentType> documentType = getDocumentTypeFromURI(fileURI);

        if (workspaceURI == null && documentType.isPresent() && (
            documentType.get() == DocumentType.SCHEMA ||
            documentType.get() == DocumentType.PROFILE
        )) {
            Optional<URI> workspaceURI = FileUtils.findSchemaDirectory(URI.create(fileURI));
            if (workspaceURI.isEmpty()) {
                messageHandler.sendMessage(MessageType.Warning, 
                    "The file " + fileURI + 
                    " does not appear to be inside a 'schemas' directory. " + 
                    "Language support will be limited.");
            } else {
                setupWorkspace(workspaceURI.get());
            }
        }

        updateFile(fileURI, document.getText(), document.getVersion());
        workspaceFiles.get(fileURI).setIsOpen(true);
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

        if (!isInWorkspace(fileURI)) {
            diagnosticsHandler.clearDiagnostics(fileURI);
        }
    }

    private void cleanUpDocument(String fileURI) {
        logger.info("Removing document: "+ fileURI);

        schemaIndex.clearDocument(fileURI);
        workspaceFiles.remove(fileURI);
        diagnosticsHandler.clearDiagnostics(fileURI);
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
        // already set up
        if (this.workspaceURI != null) return;

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

    private boolean isInWorkspace(String fileURI) {
        if (workspaceURI == null) return false;
        Path filePath = Paths.get(URI.create(fileURI));
        Path workspacePath = Paths.get(workspaceURI);
        return filePath.startsWith(workspacePath);
    }
}
