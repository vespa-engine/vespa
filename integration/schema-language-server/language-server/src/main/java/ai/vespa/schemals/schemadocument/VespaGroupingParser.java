package ai.vespa.schemals.schemadocument;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.parser.grouping.GroupingParser;
import ai.vespa.schemals.parser.grouping.Node;
import ai.vespa.schemals.parser.grouping.ParseException;
import ai.vespa.schemals.parser.grouping.Token;
import ai.vespa.schemals.schemadocument.YQLDocument.YQLPartParseResult;
import ai.vespa.schemals.tree.YQLNode;

class VespaGroupingParser {

    static YQLPartParseResult parseVespaGrouping(String input, ClientLogger logger, Position offset) {

        CharSequence charSequence = input.toLowerCase();
        GroupingParser parser = new GroupingParser(charSequence);

        try {
            parser.request();
        } catch (ParseException exception) {
            logger.error(exception.getMessage());
        }

        Node node = parser.rootNode();
        YQLNode CST = new YQLNode(node, offset);
        // GroupingUtils.printTree(logger, node);

        int charsRead = parser.getToken(0).getEndOffset();

        charsRead -= removeEOFToken(CST, logger);

        return new YQLPartParseResult(List.of(), Optional.of(CST), charsRead);
    }

    /**
     * This function removes the EOF token, this token is often a dirty token that should be the beginning of a new query
     * @param CST - Concrete Syntax tree
     * @param logger - Logger
     * @return Number of chars in the EOF token
     */
    static private int removeEOFToken(YQLNode CST, ClientLogger logger) {
        logger.info("CST size: " + CST.size());
        if (CST.size() == 0) return 0;

        YQLNode child = CST.get(CST.size() - 1).getYQLNode();
        logger.info(child);

        if (child.getASTClass() != Token.class) {
            return 0;
        }
        Node origNode = child.getOriginalGroupingNode();
        logger.info(origNode.getType());
        if (origNode.getType() != Token.TokenType.EOF) {
            return 0;
        }

        CST.removeChild(CST.size() - 1);
        return origNode.getLength();

    }
}
