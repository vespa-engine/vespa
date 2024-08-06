package ai.vespa.schemals;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.CodeActionOptions;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.ExecuteCommandOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.InitializedParams;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.RenameOptions;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.jsonrpc.CompletableFutures;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.lsp.command.CommandRegistry;
import ai.vespa.schemals.lsp.semantictokens.SchemaSemanticTokens;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

public class SchemaLanguageServer implements LanguageServer, LanguageClientAware {

    private WorkspaceService workspaceService;
    private SchemaTextDocumentService textDocumentService;
    private SchemaDocumentScheduler schemaDocumentScheduler;
    private SchemaIndex schemaIndex;
    private SchemaDiagnosticsHandler schemaDiagnosticsHandler;
    private SchemaMessageHandler schemaMessageHandler;

    private PrintStream logger;

    // Error code starts at 1 and turns into 0 if we receive shutdown request.
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
        this.schemaDocumentScheduler = new SchemaDocumentScheduler(logger, schemaDiagnosticsHandler, schemaIndex, schemaMessageHandler);

        this.textDocumentService = new SchemaTextDocumentService(this.logger, schemaDocumentScheduler, schemaIndex, schemaMessageHandler);
        this.workspaceService = new SchemaWorkspaceService(this.logger, schemaDocumentScheduler, schemaIndex, schemaMessageHandler);

    }    

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams) {
        final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());

        schemaMessageHandler.setTraceValue(initializeParams.getTrace());

        // Set the LSP capabilities of the server to inform the client.
        initializeResult.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        var completionOptions = new CompletionOptions();
        completionOptions.setTriggerCharacters(List.of("."));
        initializeResult.getCapabilities().setCompletionProvider(completionOptions);
        initializeResult.getCapabilities().setHoverProvider(true);
        initializeResult.getCapabilities().setDefinitionProvider(true);
        initializeResult.getCapabilities().setReferencesProvider(true);
        initializeResult.getCapabilities().setRenameProvider(new RenameOptions(true));
        initializeResult.getCapabilities().setSemanticTokensProvider(SchemaSemanticTokens.getSemanticTokensRegistrationOptions());
        initializeResult.getCapabilities().setDocumentSymbolProvider(true);
        initializeResult.getCapabilities().setExecuteCommandProvider(new ExecuteCommandOptions(CommandRegistry.getSupportedCommandList()));

        var options = new CodeActionOptions(List.of( 
            CodeActionKind.QuickFix,
            CodeActionKind.Refactor,
            CodeActionKind.RefactorRewrite
        ));
        //options.setResolveProvider(true);
        initializeResult.getCapabilities().setCodeActionProvider(options);


        return CompletableFuture.supplyAsync(()->initializeResult);
    }

    @Override
    public void setTrace(SetTraceParams params) {
        schemaMessageHandler.setTraceValue(params.getValue());
    }

    @Override
    public void initialized(InitializedParams params) {
        // Here we can register additional things that 
        // requires initialization to be finished

        //startWatchingFiles();
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        errorCode = 0;
        return CompletableFutures.computeAsync(tmp -> {
            logger.println("Shutdown request received.");
            return null;
        });
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
        this.client.logMessage(new MessageParams(MessageType.Log, "Language Server successfully connected to client."));

    }
}
