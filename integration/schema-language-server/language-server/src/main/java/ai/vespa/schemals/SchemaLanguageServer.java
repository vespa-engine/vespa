
package ai.vespa.schemals;

import java.io.PrintStream;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.logging.SimpleFormatter;

public class SchemaLanguageServer {

    private PrintStream logger;

    public static void main(String[] args) {
        System.out.println("This function may be useful at one point");
    }

    public SchemaLanguageServer() {
        this(null);
    }

    public SchemaLanguageServer(PrintStream _logger) {
        if (_logger == null) {
            logger = System.out;
        } else {
            logger = _logger;
        }


        logger.println("Hello World!");
    }
}