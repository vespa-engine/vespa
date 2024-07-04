package ai.vespa.schemals.tree.indexinglanguage;


import java.io.PrintStream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.indexinglanguage.Node;
import ai.vespa.schemals.parser.indexinglanguage.TokenSource;

public class ILUtils {
    private static Position getPositionFromOffset(TokenSource tokenSource, int offset) {
        int line = tokenSource.getLineFromOffset(offset) - 1;
        int startOfLineOffset = tokenSource.getLineStartOffset(line + 1);
        int column = offset - startOfLineOffset;
        return new Position(line, column);
    }

    public static Range getRangeFromOffsets(TokenSource tokenSource, int beginOffset, int endOffset) {
        Position begin = getPositionFromOffset(tokenSource, beginOffset);
        Position end = getPositionFromOffset(tokenSource, endOffset);
        return new Range(begin, end);
    }

    public static Range getNodeRange(Node node) {
        TokenSource tokenSource = node.getTokenSource();
        return getRangeFromOffsets(tokenSource, node.getBeginOffset(), node.getEndOffset());
    }

    public static void printTree(PrintStream logger, Node node) {
        printTree(logger, node, 0);
    }

    public static void printTree(PrintStream logger, Node node, Integer indent) {
        Range range = getNodeRange(node);
        logger.println(new String(new char[indent]).replace("\0", "\t") + node.getClass().getName()
            + ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ") - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")"
        );

        for (Node child : node) {
            printTree(logger, child, indent + 1);
        }
    }

}
