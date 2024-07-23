package ai.vespa.schemals;

import ai.vespa.schemals.common.FileUtils;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;
import ai.vespa.schemals.semantictokens.SchemaSemanticTokens;

import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DidChangeWatchedFilesRegistrationOptions;
import org.eclipse.lsp4j.FileSystemWatcher;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.Registration;
import org.eclipse.lsp4j.RegistrationParams;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SchemaLanguageServer implements LanguageServer, LanguageClientAware {

    private WorkspaceService workspaceService;
    private SchemaTextDocumentService textDocumentService;
    private SchemaDocumentScheduler schemaDocumentScheduler;
    private SchemaIndex schemaIndex;
    //private LanguageClient client;
    private SchemaDiagnosticsHandler schemaDiagnosticsHandler;
    private SchemaMessageHandler schemaMessageHandler;

    private PrintStream logger;
    private int errorCode = 1;

    private LanguageClient client;

    public static void main(String[] args) {
        System.out.println("This function may be useful at one point");
    }

    public SchemaLanguageServer() {
        this(null);
    }

    public SchemaLanguageServer(PrintStream logger) {
        if (logger == null) {
            this.logger = System.out;
        } else {
            this.logger = logger;
        }

        this.logger.println("Starting language server...");

        this.schemaIndex = new SchemaIndex(logger);
        this.schemaDiagnosticsHandler = new SchemaDiagnosticsHandler(logger);
        this.schemaMessageHandler = new SchemaMessageHandler(logger);
        this.schemaDocumentScheduler = new SchemaDocumentScheduler(logger, schemaDiagnosticsHandler, schemaIndex);

        this.textDocumentService = new SchemaTextDocumentService(this.logger, schemaDocumentScheduler, schemaIndex, schemaMessageHandler);
        this.workspaceService = new SchemaWorkspaceService(this.logger, schemaDocumentScheduler);

    }    

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams) {
        final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());

        // TODO: support multiple workspaces?
        String workspaceRootURI = initializeParams.getWorkspaceFolders().get(0).getUri();
        this.schemaIndex.setWorkspaceURI(workspaceRootURI);

        // Set the capabilities of the LS to inform the client.
        initializeResult.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        initializeResult.getCapabilities().setCompletionProvider(new CompletionOptions());
        initializeResult.getCapabilities().setHoverProvider(true);
        initializeResult.getCapabilities().setDefinitionProvider(true);
        initializeResult.getCapabilities().setReferencesProvider(true);
        initializeResult.getCapabilities().setRenameProvider(new RenameOptions(true));
        initializeResult.getCapabilities().setSemanticTokensProvider(SchemaSemanticTokens.getSemanticTokensRegistrationOptions());

        var options = new CodeActionOptions(new ArrayList<>() {{ add(CodeActionKind.QuickFix);}});
        //options.setResolveProvider(true); TODO: lazy resolve
        initializeResult.getCapabilities().setCodeActionProvider(options);

        this.schemaDocumentScheduler.setReparseDescendants(false);
        for (var folder : initializeParams.getWorkspaceFolders()) {
            for (String fileURI : FileUtils.findSchemaFiles(folder.getUri(), this.logger)) {
                this.schemaDocumentScheduler.addDocument(fileURI);
            }

            for (String fileURI : FileUtils.findRankProfileFiles(folder.getUri(), this.logger)) {
                this.schemaDocumentScheduler.addDocument(fileURI);
            }
        }
        this.schemaDocumentScheduler.reparseInInheritanceOrder();
        this.schemaDocumentScheduler.setReparseDescendants(true);

        return CompletableFuture.supplyAsync(()->initializeResult);
    }

    @Override
    public void initialized(InitializedParams params) {
        // Here we can register additional things that 
        // requires initialization to be finished

        startWatchingFiles();
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        errorCode = 0;
        return null;
    }

    @Override
    public void exit() {
        System.exit(errorCode);
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public void connect(LanguageClient languageClient) {
        this.client = languageClient;
        this.schemaDiagnosticsHandler.connectClient(languageClient);
        this.schemaMessageHandler.connectClient(languageClient);

    }

    void startWatchingFiles() {
        if (this.client == null) return;
        // TODO: watch xml and rank-profiles?
        List<FileSystemWatcher> watchers = new ArrayList<>() {{
            add(new FileSystemWatcher(Either.forLeft("**/*.sd")));
            add(new FileSystemWatcher(Either.forLeft("**/*/*.profile")));
        }};

        DidChangeWatchedFilesRegistrationOptions options = new DidChangeWatchedFilesRegistrationOptions(watchers);
        Registration registration = new Registration(UUID.randomUUID().toString(), "workspace/didChangeWatchedFiles", options);
        this.client.registerCapability(new RegistrationParams(Collections.singletonList(registration)));
    }

}
