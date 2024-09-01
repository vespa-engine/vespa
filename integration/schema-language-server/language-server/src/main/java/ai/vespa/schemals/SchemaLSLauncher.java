package ai.vespa.schemals;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * SchemaLSLauncher starts the language server, depeninging on the command line arguments the server can attach to different streams.
 * Default is to attach to standard in / out stream for communication
 */
public class SchemaLSLauncher {

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        LogManager.getLogManager().reset();
        Logger globaLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        globaLogger.setLevel(Level.OFF);

        startServer(System.in, System.out);
    }

    private static void startServer(InputStream in, OutputStream out) throws ExecutionException, InterruptedException {
        SchemaLanguageServer schemaLanguageServer = new SchemaLanguageServer();

        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(schemaLanguageServer, in, out);

        LanguageClient client = launcher.getRemoteProxy();

        schemaLanguageServer.connect(client);

        Future<?> startListening = launcher.startListening();

        startListening.get();
    }
}
