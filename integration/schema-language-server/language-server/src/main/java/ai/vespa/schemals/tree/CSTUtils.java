package ai.vespa.schemals.tree;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.*;

public class CSTUtils {

    private static Position getPositionFromOffset(Node node, int offset) {
        TokenSource token = node.getTokenSource();
        int line = token.getLineFromOffset(offset) - 1;
        int startOfLineOffset = token.getLineStartOffset(line + 1);
        int column = offset - startOfLineOffset;
        return new Position(line, column);
    }

    public static Range getNodeRange(Node node) {
        Position start = getPositionFromOffset(node, node.getBeginOffset());
        Position end = getPositionFromOffset(node, node.getEndOffset());
        return new Range(start, end);
    }
}
