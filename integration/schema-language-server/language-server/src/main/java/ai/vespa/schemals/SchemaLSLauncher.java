
package ai.vespa.schemals;

import ai.vespa.schemals.parser.ParserWrapper;

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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SchemaLSLauncher {

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {

        PrintStream logger;

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("-c")) {
                logger = System.out;
            } else if (args[0].equalsIgnoreCase("debug")) {
                debug(System.out);
                return;
            } else if (args[0].equalsIgnoreCase("-t")) {

                String filename = (args.length >= 2) ? args[1] : "debug.log";
                logger =  new PrintStream(
                    new FileOutputStream(filename, true)
                );
            } else {
                if (!args[0].equalsIgnoreCase("--help") && !args[0].equalsIgnoreCase("-h")) {
                    System.out.println("Invalid argument");
                    System.out.println();
                }

                System.out.println("Usage:");
                System.out.println("  -h | --help   : Displays this help message. ");
                System.out.println("  -c            : Logging will be in the standard output. ");
                System.out.println("  -t [filename] : Logging target is set to a file, with an optional filename. ");
                System.out.println("If no argument is given logging is turned off.");
                return;
            }
        } else {
            logger = new PrintStream(
                new OutputStream() {
                    public void write(int b) {
                        return;
                    }
                }
            );
        }

        LogManager.getLogManager().reset();
        Logger globaLogger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
        globaLogger.setLevel(Level.OFF);

        startServer(System.in, System.out, logger);
    }

    static public void debug(PrintStream logger) {
        String input;
        try {
            input = Files.readString(Paths.get("input.sd"));
        } catch(IOException e) {
            System.err.println("Could not read file");
            return;
        }
        
        ParserWrapper parser = new ParserWrapper(logger);
        parser.debug(input);
    }

    private static void startServer(InputStream in, OutputStream out, PrintStream logger) throws ExecutionException, InterruptedException {
        SchemaLanguageServer schemaLanguageServer = new SchemaLanguageServer(logger);

        Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(schemaLanguageServer, in, out);

        LanguageClient client = launcher.getRemoteProxy();

        schemaLanguageServer.connect(client);

        Future<?> startListening = launcher.startListening();

        startListening.get();
    }
}