package ai.vespa.schemals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

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

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.documentation.FetchDocumentation;
import ai.vespa.schemals.index.SchemaIndex;
import ai.vespa.schemals.lsp.command.CommandRegistry;
import ai.vespa.schemals.lsp.semantictokens.SchemaSemanticTokens;
import ai.vespa.schemals.schemadocument.SchemaDocumentScheduler;

/**
 * SchemaLanguageServer represents the languageserver.
 * It handles start and shutdown, and initializes the server's capabilities
 */
public class SchemaLanguageServer implements LanguageServer, LanguageClientAware {

    public static Path serverPath = null;

    private WorkspaceService workspaceService;
    private SchemaTextDocumentService textDocumentService;
    private SchemaDocumentScheduler schemaDocumentScheduler;
    private SchemaIndex schemaIndex;
    private SchemaDiagnosticsHandler schemaDiagnosticsHandler;
    private SchemaMessageHandler schemaMessageHandler;

    // Error code starts at 1 and turns into 0 if we receive shutdown request.
    private int errorCode = 1;

    private LanguageClient client;
    private ClientLogger logger;

    public static void main(String[] args) {
        System.out.println("This function may be useful at one point");
    }

    /**
     * Note: Everything happening here, before connect, should not log. All logging here goes nowhere.
     */
    public SchemaLanguageServer() {
        this.schemaMessageHandler = new SchemaMessageHandler();
        this.logger = new ClientLogger(schemaMessageHandler);
        this.schemaIndex = new SchemaIndex(logger);
        this.schemaDiagnosticsHandler = new SchemaDiagnosticsHandler();
        this.schemaDocumentScheduler = new SchemaDocumentScheduler(logger, schemaDiagnosticsHandler, schemaIndex, schemaMessageHandler);

        this.textDocumentService = new SchemaTextDocumentService(this.logger, schemaDocumentScheduler, schemaIndex, schemaMessageHandler);
        this.workspaceService = new SchemaWorkspaceService(this.logger, schemaDocumentScheduler, schemaIndex, schemaMessageHandler);
        serverPath = Paths.get(SchemaLanguageServer.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParent();
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
            logger.info("Shutdown request received.");
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

        if (serverPath == null) return;

        // Start a document fetching job in the background.
        Path docPath = serverPath.resolve("hover");

        try {
            setupDocumentation(docPath);
        } catch (IOException ioex) {
            this.logger.error("Failed to set up documentation. Error: " + ioex.getMessage());
        }
    }

    /**
     * Initial setup of the documentation.
     */
    public void setupDocumentation(Path documentationPath) throws IOException {

        Files.createDirectories(documentationPath);
        Files.createDirectories(documentationPath.resolve("schema"));
        Files.createDirectories(documentationPath.resolve("rankExpression"));

        ensureLocalDocumentationLoaded(documentationPath);

        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    FetchDocumentation.fetchDocs(documentationPath);
                } catch(Exception e) {
                    throw new RuntimeException(e.getMessage());
                }
            }
        };
        var logger = this.logger;
        thread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread th, Throwable ex) {
                logger.warning("Failed to fetch docs: " + ex.getMessage() + " is unavailable. Locally cached documentation will be used.");
            }
        });
        logger.info("Fetching docs to path: " + documentationPath.toAbsolutePath().toString());
        thread.start();

    }

    /**
     * Assumes documentation is loaded if documentationPath/schema contains .md files.
     * If documentation is not loaded, unpacks markdown files from the current jar.
     */
    private void ensureLocalDocumentationLoaded(Path documentationPath) throws IOException {
        File dir = new File(documentationPath.resolve("schema").toString());
        File[] contents = dir.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".md");
            }
        });
        // Documentation exists
        if (contents.length > 0) return;

        logger.info("Extracting embedded documentation files.");

        // If it doesn't exist, unpack from jar
        var resources = Thread.currentThread().getContextClassLoader().getResources(documentationPath.getFileName().toString());

        if (!resources.hasMoreElements()) {
            throw new IOException("Could not find documentation in jar file!");
        }

        URL resourceURL = resources.nextElement();

        if (!resourceURL.getProtocol().equals("jar")) {
            throw new IOException("Unhandled protocol for resource " +  resourceURL.toString());
        }

        String jarPath = resourceURL.getPath().substring(5, resourceURL.getPath().indexOf('!'));
        try (JarFile jarFile = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().startsWith(documentationPath.getFileName().toString())) {
                    Path destination = documentationPath.getParent().resolve(entry.getName());
                    try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(entry.getName())) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                        String content = reader.lines().collect(Collectors.joining(System.lineSeparator()));
                        Files.write(destination, content.getBytes(), StandardOpenOption.CREATE);
                    }
                }
            }
        }
    }
}
