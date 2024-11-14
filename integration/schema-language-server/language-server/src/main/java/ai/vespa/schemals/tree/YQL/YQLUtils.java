package ai.vespa.schemals.tree.YQL;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.parser.yqlplus.Node;
import ai.vespa.schemals.parser.yqlplus.TokenSource;
import ai.vespa.schemals.tree.CSTUtils;

public class YQLUtils {

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

    public static void printTree(ClientLogger logger, Node node) {
        printTree(logger, node, 0);
    }

    public static void printTree(ClientLogger logger, Node node, Integer indent) {
        Range range = getNodeRange(node);
        logger.info(new String(new char[indent]).replace("\0", "\t") + node.getClass().getName()
            + ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ") - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")"
        );

        for (Node child : node) {
            printTree(logger, child, indent + 1);
        }
    }

    public static void printTree(ClientLogger logger, ai.vespa.schemals.tree.Node node) {
        printTree(logger, node, 0);
    }

    public static void printTree(ClientLogger logger, ai.vespa.schemals.tree.Node node, int indent) {
        String padding = new String(new char[indent]).replace("\0", CSTUtils.SPACER);
        logger.info(padding +  node.toString());
        for (ai.vespa.schemals.tree.Node child : node) {
            printTree(logger, child, indent + 1);
        }
    }
}
