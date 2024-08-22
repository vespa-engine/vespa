package ai.vespa.lemminxvespa.util;

import java.io.PrintStream;

import org.eclipse.lemminx.dom.DOMNode;

/**
 * LoggerUtils
 */
public class LoggerUtils {
    public static void printDOM(PrintStream logger, DOMNode node) {
        printDOM(logger, node, 0);
    }

    private static void printDOM(PrintStream logger, DOMNode node, int indent) {
        logger.println(new String(new char[indent]).replaceAll("\0", "\t") + node.getNodeName());
        for (DOMNode child : node.getChildren()) {
            printDOM(logger, child, indent + 1);
        }
    }
}
