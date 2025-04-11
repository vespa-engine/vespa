package ai.vespa.schemals.lsp.schema.formatting;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentEdit;
import org.eclipse.lsp4j.TextEdit;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventFormattingContext;
import ai.vespa.schemals.parser.ast.LBRACE;
import ai.vespa.schemals.parser.ast.NL;
import ai.vespa.schemals.parser.ast.openLbrace;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

public class SchemaFormatting {
    private record FormatPositionInformation(int indentLevel, boolean nlBeforeSibling) {}

    /*
     * Compute text edits that will prettify the document.
     */
    public static List<TextEdit> computeFormattingEdits(EventFormattingContext context) {
        context.logger.info("received formatting request with params: " + context.getOptions().toString());
        SchemaNode root = context.document.getRootNode();
        List<TextEdit> result = new ArrayList<>();
        FormatPositionInformation info = new FormatPositionInformation(0, false);
        formatTraverse(result, root, info, context.getOptions(), context.logger);
        return result;
    }

    private static void formatTraverse(
        List<TextEdit> edits, 
        SchemaNode node, 
        FormatPositionInformation info, 
        FormattingOptions options, 
        ClientLogger logger) 
    {
        if (!node.isSchemaNode()) {
            // TODO: Format YQL, RankExpressions and Indexing
            return;
        }

        if (node.isLeaf()) {
            return;
        }

        logger.info(node.getASTClass().toString() + ": " + node.getRange().getStart().toString());

        int lbraceIndex = -1;

        for (int i = 0; i < node.size(); ++i) {
            if (node.get(i).isASTInstance(openLbrace.class)) {
                lbraceIndex = i;
                break;
            }
        }

        if (lbraceIndex != -1) {
            SchemaNode lbraceNode = node.get(lbraceIndex).getSchemaNode();
            // Has to have a previous sibling
            SchemaNode nodeBeforeLbrace = lbraceNode.getPreviousSibling().getSchemaNode();
            while (nodeBeforeLbrace.isASTInstance(NL.class))
                nodeBeforeLbrace = nodeBeforeLbrace.getPreviousSibling().getSchemaNode();

            SchemaNode LBRACENode = null;

            for (Node child : node.get(lbraceIndex)) {
                if (!child.isSchemaNode()) continue;
                if (child.isASTInstance(LBRACE.class)) {
                    LBRACENode = child.getSchemaNode();
                    break;
                }
            }

            if (LBRACENode.getRange().getStart().getLine() != nodeBeforeLbrace.getRange().getEnd().getLine()
                || LBRACENode.getRange().getStart().getCharacter() != nodeBeforeLbrace.getRange().getEnd().getCharacter() + 1) {
                edits.add(new TextEdit(new Range(
                    nodeBeforeLbrace.getRange().getEnd(), 
                    LBRACENode.getRange().getStart()
                ), " "));
            }

            if (LBRACENode.getNextSibling() == null) {
                // No new line after lbrace
                edits.add(new TextEdit(new Range(
                    LBRACENode.getRange().getEnd(),
                    LBRACENode.getRange().getEnd()
                ), "\n"));
            }
        }

        for (int i = 0; i < node.size(); ++i) {
            Node child = node.get(i);
            if (!child.isSchemaNode()) continue;
            boolean requireNewLine = lbraceIndex != -1 && i > lbraceIndex;
            formatTraverse(
                edits, 
                child.getSchemaNode(), 
                new FormatPositionInformation(info.indentLevel, requireNewLine), 
                options, 
                logger
            );
        }
    }
}
