package ai.vespa.schemals.tree;

import java.io.PrintStream;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.tree.indexinglanguage.ILUtils;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;

public class CSTUtils {

    public static Position getPositionFromOffset(TokenSource tokenSource, int offset) {
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


    public static boolean positionLT(Position lhs, Position rhs) {
        return (
            lhs.getLine() < rhs.getLine() || (
                lhs.getLine() == rhs.getLine() && 
                lhs.getCharacter() < rhs.getCharacter()
            )
        );
    }

    public static boolean positionInRange(Range range, Position pos) {
        return (
            positionLT(pos, range.getEnd()) && (
                positionLT(range.getStart(), pos) ||
                range.getStart().equals(pos)
            )
        );
    }

    public static Position addPositions(Position lhs, Position rhs) {
        int line = lhs.getLine() + rhs.getLine();
        int column = rhs.getCharacter();
        if (rhs.getLine() == 0) {
            column += lhs.getCharacter();
        }

        return new Position(line, column);
    }

    public static SchemaNode findFirstLeafChild(SchemaNode node) {
        while (!node.isLeaf()) {
            node = node.get(0);
        }
        return node;
    }

    /* Returns the last non-dirty node before the supplied position */
    public static SchemaNode getLastCleanNode(SchemaNode node, Position pos) {
        for (int i = node.size() - 1; i >= 0; i--) {
            SchemaNode result = getLastCleanNode(node.get(i), pos);
            if (result != null)return result;
        }

        Range range = node.getRange();
        if (!positionLT(pos, range.getStart()) && !node.isDirty()) {
            return node;
        }

        return null;
    }

    /*
     * Logger utils
     * */


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

    public static void printTree(PrintStream logger, SchemaNode node) {
        printTree(logger, node, 0);
    }

    public static void printTree(PrintStream logger, SchemaNode node, Integer indent) {

        logger.println(new String(new char[indent]).replace("\0", "\t") + schemaNodeString(node));

        for (SchemaNode child : node) {
            printTree(logger, child, indent + 1);
        }

        if (node.hasIndexingNode()) {
            ILUtils.printTree(logger, node.getIndexingNode(), indent + 1);
        }

        if (node.hasFeatureListNode()) {
            RankingExpressionUtils.printTree(logger, node.getFeatureListNode(), indent + 1);
        }
    }

    public static void printTreeUpToPosition(PrintStream logger, Node node, Position pos) {
        printTreeUpToPosition(logger, node, pos, 0);
    }

    public static void printTreeUpToPosition(PrintStream logger, SchemaNode node, Position pos) {
        printTreeUpToPosition(logger, node, pos, 0);
    }

    public static void printTreeUpToPosition(PrintStream logger, Node node, Position pos, Integer indent) {
        Range range = getNodeRange(node);

        if (!positionLT(pos, range.getStart())) {
            boolean dirty = node.isDirty();
            logger.println(new String(new char[indent]).replace("\0", "\t") + node.getClass().getName() + (dirty ? " [DIRTY]" : "")
            + ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ") - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")"
                    );
        }

        for (Node child : node) {
            printTreeUpToPosition(logger, child, pos, indent + 1);
        }
    }

    public static void printTreeUpToPosition(PrintStream logger, SchemaNode node, Position pos, Integer indent) {

        Range range = node.getRange();
        if (!positionLT(pos, range.getStart())) {
            boolean dirty = node.isDirty();
            logger.println(new String(new char[indent]).replace("\0", "\t") + schemaNodeString(node));
        }


        for (SchemaNode child : node) {
            printTreeUpToPosition(logger, child, pos, indent + 1);
        }
    }

    private static String schemaNodeString(SchemaNode node) {

        String ret = node.getClassLeafIdentifierString();

        if (node.isDirty()) {
            ret += " [DIRTY]";
        }

        if (node.isIndexingElm()) {
            ret += " [ILSCRIPT]";
        }

        if (node.isFeatureListElm()) {
            ret += " [FEATURES]";
        }

        if (node instanceof SymbolReferenceNode) {
            ret += " [REF]";
        }

        Range range = node.getRange();
        ret += ": (" + range.getStart().getLine() + ", " + range.getStart().getCharacter() + ")";
        ret += " - (" + range.getEnd().getLine() + ", " + range.getEnd().getCharacter() + ")";

        return ret;
    }
}
