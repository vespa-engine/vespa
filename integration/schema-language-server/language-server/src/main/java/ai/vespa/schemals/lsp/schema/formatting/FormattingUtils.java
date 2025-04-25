package ai.vespa.schemals.lsp.schema.formatting;

import java.util.Optional;
import java.util.List;

import org.eclipse.lsp4j.FormattingOptions;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.tree.Node;

/*
 * Helper functions that do not perform formatting themselves.
 */
class FormattingUtils {
    record LineRange(
        int firstLine,
        int lastLine
    ) {}

    record FormatPositionInformation(
        // One unit of indent is one tab or "tabSize" spaces.
        int indentLevel, 
        // Indicates if the current node starts a new line during traversal.
        boolean nodeStartsLine,
        Optional<LineRange> formatRange
    ) {}

    static int computeNodeIndentLevel(Node containing, FormattingOptions options) {
        int startCharacter = containing.getRange().getStart().getCharacter();
        if (options.isInsertSpaces()) {
            // return ceil
            return startCharacter / options.getTabSize();
        }
        // assume its already indented using tabs
        return startCharacter;
    }

    /*
     * Returns kind of a minimal covering set of nodes for the given (line)range,
     * i.e. it never includes two nodes with an ancestor relationship in the result.
     */
    static void findNodesInLineRange(Node node, LineRange range, List<Node> result) {
        int nodeFirstLine = node.getRange().getStart().getLine();
        if (nodeFirstLine > range.lastLine()) return;
        if (nodeFirstLine >= range.firstLine()) {
            result.add(node);
            return;
        }

        for (Node child : node) {
            findNodesInLineRange(child, range, result);
        }
    }

    static Node getFirstLbraceAncestor(Node node) {
        for (Node parent = node.getParent(); parent != null; parent = parent.getParent()) {
            if (getNodeLbraceIndex(parent) != -1) return parent;
        }
        return null;
    }

    static int getNodeLbraceIndex(Node node) {
        for (int i = 0; i < node.size(); ++i) {
            if (node.get(i).isASTInstance(openLbrace.class)) {
                return i;
            }
        }
        return -1;
    }

    /*
     * Gives a string of either tabs or spaces, depending on options
     */
    static String createIndentationString(int indentLevel, FormattingOptions options) {
        if (indentLevel == 0) return "";
        if (options.isInsertSpaces()) {
            return new String(new char[indentLevel * options.getTabSize()]).replace("\0", " ");
        }
        return new String(new char[indentLevel]).replace("\0", "\t");
    }


    static boolean shouldHaveSpaceBetween(Node left, Node right) {
        String leftText = left.getLastLeafDescendant().getText();
        String rightText = right.getText();
        if (leftText.isEmpty() || rightText.isEmpty()) return false;

        int leftLast   = leftText.codePointAt(leftText.length() - 1);
        int rightFirst = rightText.codePointAt(0);
        if (Character.isLetterOrDigit(rightFirst) && Character.isLetterOrDigit(leftLast)) return true;
        if (rightFirst == '.' || rightFirst == ':') return false;
        if (leftLast == ':' || leftLast == ',' || leftLast == ')') return true;
        return false;
    }

    static boolean isEOF(Node node) {
        if (node == null) return false;
        if (node.getSchemaNode() == null) return false;
        if (node.getSchemaNode().getOriginalSchemaNode() == null) return false;
        return node.getSchemaNode().getOriginalSchemaNode().getType() == TokenType.EOF;
    }
}
