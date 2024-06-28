
package ai.vespa.schemals;

import ai.vespa.schemals.context.SchemaDocumentScheduler;
import ai.vespa.schemals.context.SchemaIndex;
import ai.vespa.schemals.semantictokens.SchemaSemanticTokens;

import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.DefinitionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;

import java.io.PrintStream;
import java.util.concurrent.CompletableFuture;

public class SchemaLanguageServer implements LanguageServer, LanguageClientAware {

    private WorkspaceService workspaceService;
    private SchemaTextDocumentService textDocumentService;
    private SchemaDocumentScheduler schemaDocumentScheduler;
    private SchemaIndex schemaIndex;
    //private LanguageClient client;
    private SchemaDiagnosticsHandler schemaDiagnosticsHandler;

    private PrintStream logger;
    private int errorCode = 1;

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
        this.schemaDocumentScheduler = new SchemaDocumentScheduler(logger, schemaDiagnosticsHandler, schemaIndex);

        this.textDocumentService = new SchemaTextDocumentService(this.logger, schemaDocumentScheduler, schemaIndex);
        this.workspaceService = new SchemaWorkspaceService();
    }    

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams) {
        final InitializeResult initializeResult = new InitializeResult(new ServerCapabilities());

        // Set the capabilities of the LS to inform the client.
        initializeResult.getCapabilities().setTextDocumentSync(TextDocumentSyncKind.Full);
        CompletionOptions completionOptions = new CompletionOptions();
        initializeResult.getCapabilities().setCompletionProvider(completionOptions);
        initializeResult.getCapabilities().setHoverProvider(true);
        initializeResult.getCapabilities().setDefinitionProvider(true);
        initializeResult.getCapabilities().setSemanticTokensProvider(SchemaSemanticTokens.getSemanticTokensRegistrationOptions());
        return CompletableFuture.supplyAsync(()->initializeResult);
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
        //this.client = languageClient;
        this.schemaDiagnosticsHandler.connectClient(languageClient);
    }
}