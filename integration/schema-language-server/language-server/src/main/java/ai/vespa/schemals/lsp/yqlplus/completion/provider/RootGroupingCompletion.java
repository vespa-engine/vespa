package ai.vespa.schemals.lsp.yqlplus.completion.provider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.common.completion.CompletionProvider;
import ai.vespa.schemals.lsp.common.completion.CompletionUtils;
import ai.vespa.schemals.parser.grouping.ast.SPACE;
import ai.vespa.schemals.parser.grouping.ast.rbraceElm;
import ai.vespa.schemals.parser.grouping.ast.request;
import ai.vespa.schemals.parser.grouping.ast.spaceElm;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.YQLNode;
import ai.vespa.schemals.tree.Node.LanguageType;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class RootGroupingCompletion implements CompletionProvider {

    final private List<CompletionItem> items = new ArrayList<>() {{
        add(CompletionUtils.constructSnippet("all", "all($1)$0"));
        add(CompletionUtils.constructSnippet("each", "each($1)$0"));
    }};

    @Override
    public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        List<CompletionItem> empty = List.of();

        Node last = CSTUtils.getLastCleanNode(context.document.getRootYQLNode(), CSTUtils.subtractOneChar(context.position));

        if (last == null) {
            return empty;
        }

        if (!last.isYQLNode()) {
            throw new IllegalArgumentException("Unexpected node type, expected a YQLNode");
        }

        if (last.getLanguageType() == LanguageType.CUSTOM && last.getText() == "|") {
            return items;
        }

        if (last.getLanguageType() != LanguageType.GROUPING) {
            return empty;
        }

        YQLNode node = last.getYQLNode();

        context.logger.info("NODE");
        YQLUtils.printTree(context.logger, node);

        if (node.isASTInstance(SPACE.class)) {
            node = node.getParent().getYQLNode();
        }

        if (node.isASTInstance(spaceElm.class) && node.getParent().isASTInstance(request.class)) {
            return items;
        }


        Node possibleRBraceNode = node.getParent();
        Node possibleRequestNode = node.getParent(5);
        YQLUtils.printTree(context.logger, possibleRequestNode);
        if (
            possibleRBraceNode != null &&
            possibleRBraceNode.isASTInstance(rbraceElm.class) &&
            possibleRequestNode != null &&
            possibleRequestNode.isASTInstance(request.class)
        ) {
            return List.of(CompletionUtils.constructSnippet("where", "where($0)"));
        }

        return empty;
    }
}
