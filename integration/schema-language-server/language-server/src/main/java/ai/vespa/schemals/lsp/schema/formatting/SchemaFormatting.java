package ai.vespa.schemals.lsp.schema.formatting;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventFormattingContext;
import ai.vespa.schemals.parser.ast.LBRACE;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.RBRACE;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.Node.LanguageType;

public class SchemaFormatting {
    private record FormatPositionInformation(
            // One unit of indent is one tab or "tabSize" spaces.
            int indentLevel, 
            // Indicates if the current node starts a new line during traversal.
            boolean nodeStartsLine
        ) {}

    /*
     * Compute text edits that will prettify the document.
     */
    public static List<TextEdit> computeFormattingEdits(EventFormattingContext context) {
        SchemaNode root = context.document.getRootNode();
        List<TextEdit> result = new ArrayList<>();
        FormatPositionInformation info = new FormatPositionInformation(0, true);
        formatTraverse(result, root, info, context.getOptions(), context.logger);
        if (context.getOptions().isTrimTrailingWhitespace()) {
        }
        return result;
    }

    private static void formatTraverse(
        List<TextEdit> edits, 
        Node node, 
        FormatPositionInformation info, 
        FormattingOptions options, 
        ClientLogger logger) 
    {
        if (!node.isSchemaNode()) {
            // TODO: Format YQL, RankExpressions and Indexing
            return;
        }
        if (node.getLanguageType() != LanguageType.SCHEMA) return;


        // Don't apply formatting on NL
        if (node.isASTInstance(NL.class)) return;

        if (info.nodeStartsLine())
            formatLineStartNodePosition(edits, node, info, options);
        else
            formatLineMiddleNodePosition(edits, node, logger);

        int lbraceIndex = -1;
        for (int i = 0; i < node.size(); ++i) {
            if (node.get(i).isASTInstance(openLbrace.class)) {
                lbraceIndex = i;
                break;
            }
        }

        if (lbraceIndex != -1)
            formatLbracePosition(edits, node.get(lbraceIndex));

        for (int i = 0; i < node.size(); ++i) {
            Node child = node.get(i);

            int indentLevel = info.indentLevel;
            boolean childStartsNewLine = lbraceIndex != -1 && i > lbraceIndex;
            if (childStartsNewLine)++indentLevel;

            formatTraverse(
                edits, 
                child, 
                new FormatPositionInformation(indentLevel, childStartsNewLine), 
                options, 
                logger
            );
        }
    }

    /*
     * Nodes that start a new line are placed on their own line (if not already there)
     * Indentation is inserted before the node with indentation settings received from client.
     */
    private static void formatLineStartNodePosition(List<TextEdit> edits, Node node, FormatPositionInformation info, FormattingOptions options) {
        int indentLevel = info.indentLevel;
        if (node.isASTInstance(RBRACE.class))--indentLevel;

        String indentationString = createIndentationString(indentLevel, options);

        // Insert a new line + indent if node is not on its own line
        if (node.getPreviousSibling() != null) {
            Node shouldBeNL = node.getPreviousSibling().getLastLeafDescendant();
            if (!shouldBeNL.isASTInstance(NL.class)) {
                edits.add(new TextEdit(new Range(
                    shouldBeNL.getRange().getEnd(),
                    node.getRange().getStart()
                ), "\n" + indentationString));
                return;
            }
        }

        // Ensure indentation is good. 
        // Edits here could end up being no-ops, but editors handle it nicely.
        Range range = new Range(
            new Position(node.getRange().getStart().getLine(), 0),
            node.getRange().getStart()
        );
        edits.add(new TextEdit(range, indentationString));
    }

    private static void formatLineMiddleNodePosition(List<TextEdit> edits, Node node, ClientLogger logger) {
        if (node.getPreviousSibling() == null) return;
        if (node.getASTClass() == null) return;
        if (node.isASTInstance(LBRACE.class)) return;
        if (node.isASTInstance(openLbrace.class)) return;

        Node prev = node.getPreviousSibling();
        while (prev != null && (prev.isASTInstance(NL.class) || CSTUtils.rangeIsEmpty(prev.getRange())))
            prev = prev.getPreviousSibling();

        if (prev == null) return;

        String insertText = "";

        if (shouldHaveSpaceBetween(prev, node))
            insertText = " ";

        Range range = new Range(prev.getRange().getEnd(), node.getRange().getStart());
        edits.add(new TextEdit(
            range,
            insertText
        ));
    }

    private static boolean shouldHaveSpaceBetween(Node left, Node right) {
        String leftText = left.getLastLeafDescendant().getText();
        String rightText = right.getText();
        if (leftText.isEmpty() || rightText.isEmpty()) return false;

        int leftLast   = leftText.codePointAt(leftText.length() - 1);
        int rightFirst = rightText.codePointAt(0);
        if (Character.isLetterOrDigit(rightFirst) && Character.isLetterOrDigit(leftLast)) return true;
        if (leftLast == ':' || leftLast == ',') return true;
        return false;
    }

    /*
     * Ensures that lbrace is placed at the end of a line, with one space before it.
     */
    private static void formatLbracePosition(List<TextEdit> edits, Node lbraceNode) {
        // Has to have a previous sibling
        Node nodeBeforeLbrace = lbraceNode.getPreviousSibling();
        while (nodeBeforeLbrace.isASTInstance(NL.class))
            nodeBeforeLbrace = nodeBeforeLbrace.getPreviousSibling();

        // The leaf token LBRACE
        Node LBRACENode = lbraceNode.get(0);

        while (!LBRACENode.isASTInstance(LBRACE.class)) 
            LBRACENode = LBRACENode.getNextSibling();

        edits.add(new TextEdit(new Range(
            nodeBeforeLbrace.getRange().getEnd(), 
            LBRACENode.getRange().getStart()
        ), " "));
    }

    /*
     * Gives a string of either tabs or spaces, depending on options
     */
    private static String createIndentationString(int indentLevel, FormattingOptions options) {
        if (indentLevel == 0) return "";
        if (options.isInsertSpaces()) {
            return new String(new char[indentLevel * options.getTabSize()]).replace("\0", " ");
        }
        return new String(new char[indentLevel]).replace("\0", "\t");
    }
}
