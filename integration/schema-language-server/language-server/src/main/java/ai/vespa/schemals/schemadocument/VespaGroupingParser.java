package ai.vespa.schemals.schemadocument;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.parser.grouping.GroupingParser;
import ai.vespa.schemals.parser.grouping.Node;
import ai.vespa.schemals.parser.grouping.ParseException;
import ai.vespa.schemals.schemadocument.YQLDocument.ParseResult;
import ai.vespa.schemals.tree.YQLNode;

class VespaGroupingParser {

    static ParseResult parseVespaGrouping(String input, ClientLogger logger, Position offset) {

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

        return new ParseResult(List.of(), Optional.of(CST));
    }
}
