package ai.vespa.schemals.lsp.schema.formatting;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventFormattingContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.LBRACE;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.RBRACE;
import ai.vespa.schemals.parser.ast.Root;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.parser.ast.rootDocument;
import ai.vespa.schemals.parser.ast.rootSchema;
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

    static ClientLogger logger;

    /*
     * Compute text edits that will prettify the document.
     */
    public static List<TextEdit> computeFormattingEdits(EventFormattingContext context) {
        logger = context.logger;
        SchemaNode root = context.document.getRootNode();
        List<TextEdit> result = new ArrayList<>();
        FormatPositionInformation info = new FormatPositionInformation(0, true);
        FormattingOptions options = context.getOptions();
        formatTraverse(result, root, info, options);
        if (options.isTrimTrailingWhitespace()) {
            computeTrimTrailingWhitespaceEdits(result, context.document.getCurrentContent());
        }
        if (context.getOptions().isTrimFinalNewlines()) {
            computeTrimFinalNewlinesEdits(result, root, options.isInsertFinalNewline());
        }

        return result;
    }

    private static void formatTraverse(
        List<TextEdit> edits, 
        Node node, 
        FormatPositionInformation info, 
        FormattingOptions options) 
    {
        int curr_sz = edits.size();
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
            formatLineMiddleNodePosition(edits, node);

        int lbraceIndex = -1;
        for (int i = 0; i < node.size(); ++i) {
            if (node.get(i).isASTInstance(openLbrace.class)) {
                lbraceIndex = i;
                break;
            }
        }

        if (lbraceIndex != -1)
            formatLbracePosition(edits, node.get(lbraceIndex));

        if (edits.size() > curr_sz) {
            //logger.info(node.toString());
            //logger.info("Applied the following edits:");
            //for (int i = curr_sz; i < edits.size(); ++i) {
            //    TextEdit edit = edits.get(i);
            //    logger.info(edit.getRange().toString() + " -> \"" + edit.getNewText() + "\"");
            //}
        }

        int lastNonNLIndex = -1;
        for (int i = 0; i < node.size(); ++i) {
            Node child = node.get(i);

            int indentLevel = info.indentLevel;
            boolean childStartsNewLine = lbraceIndex != -1 && i > lbraceIndex;
            if (childStartsNewLine)++indentLevel;

            // Edge case occurring due to wrapping 'Root' element (without lbrace)
            // However, we do not want to increase indentation.
            if (child.isASTInstance(rootSchema.class) || child.isASTInstance(rootDocument.class)) childStartsNewLine = true;

            if (child.isASTInstance(RBRACE.class) && lastNonNLIndex == lbraceIndex) {
                // If many newlines after lbrace, there could be comments
                if (node.get(lbraceIndex).size() <= 2)
                    childStartsNewLine = false;
            }

            formatTraverse(
                edits, 
                child, 
                new FormatPositionInformation(indentLevel, childStartsNewLine), 
                options
            );

            if (!child.isASTInstance(NL.class))lastNonNLIndex = i;
        }
    }

    /*
     * Formats nodes that are supposed to be at the start of a line.
     */
    private static void formatLineStartNodePosition(List<TextEdit> edits, Node node, FormatPositionInformation info, FormattingOptions options) {
        if (node.isASTInstance(Root.class)) return; // pseudo-element that should not be formatted
        if (isEOF(node)) return;

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

    /*
     * Formats nodes that are not at the start of their line.
     */
    private static void formatLineMiddleNodePosition(List<TextEdit> edits, Node node) {
        if (node.getPreviousSibling() == null) return;
        if (node.getASTClass() == null) return;
        if (node.isASTInstance(LBRACE.class)) return;
        if (node.isASTInstance(openLbrace.class)) return;

        // Most nodes should have exactly one space after their previous sibling.
        // TODO: If lines are very long this may not be desired behavior.
        Node prev = node.getPreviousSibling();
        while (prev != null && (prev.isASTInstance(NL.class) || CSTUtils.rangeIsEmpty(prev.getRange())))
            prev = prev.getPreviousSibling();

        if (prev == null) return;

        String insertText = "";

        if (shouldHaveSpaceBetween(prev, node))
            insertText = " ";

        Range range = new Range(prev.getRange().getEnd(), node.getRange().getStart());

        if (prev.isASTInstance(openLbrace.class) && node.getRange().getStart().getLine() - prev.getRange().getStart().getLine() <= 1) {
            // Turning
            // {
            // }
            // Into 
            // {}
            Position lbracePos = prev.getRange().getStart();
            range = new Range(new Position(lbracePos.getLine(), lbracePos.getCharacter() + 1), node.getRange().getStart());
        }

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
        if (rightFirst == '.' || rightFirst == ':') return false;
        if (leftLast == ':' || leftLast == ',' || leftLast == ')') return true;
        return false;
    }

    private static boolean isEOF(Node node) {
        if (node == null) return false;
        if (node.getSchemaNode() == null) return false;
        if (node.getSchemaNode().getOriginalSchemaNode() == null) return false;
        return node.getSchemaNode().getOriginalSchemaNode().getType() == TokenType.EOF;
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

    /*
     * Adds edits that remove trailing whitespace on each line
     */
    private static void computeTrimTrailingWhitespaceEdits(List<TextEdit> edits, String documentContent) {
        int prevNewLine = -1;
        int nextNewLine = documentContent.indexOf('\n');
        int lineNum = 0;
        while (true) {
            if (nextNewLine == -1)nextNewLine = documentContent.length();

            int ptr = nextNewLine - 1;
            while (ptr > 0 
                && documentContent.codePointAt(ptr) != '\n' 
                && Character.isWhitespace(documentContent.codePointAt(ptr))
            ) {
                --ptr;
            }

            if (ptr < nextNewLine) {
                edits.add(new TextEdit(
                    new Range(
                        new Position(lineNum, ptr - prevNewLine),
                        new Position(lineNum, nextNewLine - prevNewLine)
                    ), ""
                ));
            }

            if (nextNewLine == documentContent.length()) break;
            prevNewLine = nextNewLine;
            nextNewLine = documentContent.indexOf('\n', nextNewLine + 1);
            lineNum++;
        }
    }

    /*
     * Adds edits that removes newlines at the end of the file
     */
    private static void computeTrimFinalNewlinesEdits(List<TextEdit> edits, Node root, boolean insertOne) {
        if (root.size() == 0) return;
        // Root -> rootSchema | rootDocument
        Node EOFNode = null;
        Node lastRBRACENode = null;
        if (root.get(0).isASTInstance(rootSchema.class)) {
            // rootSchema
            // Here, both EOF and the last RBRACE is inside rootSchema

            root = root.get(0);
            EOFNode = root.get(root.size() - 1);
            lastRBRACENode = EOFNode.getPreviousSibling();
        } else {
            // rootDocument
            // Here, EOF belongs to Root, while the last RBRACE is inside rootDocument

            EOFNode = root.get(root.size() - 1);
            root = root.get(0);
            lastRBRACENode = root.get(root.size() - 1);
        }

        while (lastRBRACENode != null && !lastRBRACENode.isASTInstance(RBRACE.class)) {
            lastRBRACENode = lastRBRACENode.getPreviousSibling();
        }

        if (EOFNode == null || lastRBRACENode == null) {
            return;
        }

        String insertString = insertOne ? "\n" : "";
        Range range = new Range(
            lastRBRACENode.getRange().getEnd(),
            EOFNode.getRange().getStart());
        edits.add(new TextEdit(range, insertString));
    }
}
