
package ai.vespa.schemals;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Launcher {

    public static void main(String[] args) throws IOException {
        Launcher(args);
    }

    public static void Launcher(String[] args) throws IOException {

        PrintStream logger;

        if (args.length > 0) {

            if (args[0].equalsIgnoreCase("-c")) {
                logger = System.out;
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

        

        // Logger logger = Logger.getLogger("DEBUG LOG");
        // FileHandler fh = new FileHandler("./debug.log"); // TODO: this should be an argument
        // fh.setFormatter(new SimpleFormatter());
        // logger.addHandler(fh);

        startServer(System.in, System.out, logger);
    }

    private static void startServer(InputStream in, OutputStream out, PrintStream logger) {
        SchemaLanguageServer schemaLanguageServer = new SchemaLanguageServer(logger);
    }
}