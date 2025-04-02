package ai.vespa.schemals.lsp.yqlplus.completion.provider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.common.completion.CompletionProvider;
import ai.vespa.schemals.lsp.common.completion.CompletionUtils;
import ai.vespa.schemals.parser.grouping.ast.SPACE;
import ai.vespa.schemals.parser.grouping.ast.request;
import ai.vespa.schemals.parser.grouping.ast.spaceElm;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.YQLNode;
import ai.vespa.schemals.tree.Node.LanguageType;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class RootGroupingCompletion implements CompletionProvider {

    @Override
    public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        List<CompletionItem> empty = new ArrayList<>();
        List<CompletionItem> items = new ArrayList<>() {{
            add(CompletionUtils.constructSnippet("all", "all($0)"));
            add(CompletionUtils.constructSnippet("each", "each($0)"));
        }};

        Position searchPos = context.position;

        Node last = CSTUtils.getLastCleanNode(context.document.getRootYQLNode(), searchPos);

        if (last == null) {
            return empty;
        }

        if (!last.isYQLNode()) {
            throw new IllegalArgumentException("Unexpected node type, expected a YQLNode");
        }

        if (last.getLanguageType() != LanguageType.GROUPING) {
            return empty;
        }

        YQLNode node = last.getYQLNode();

        context.logger.info("Grouping comp:");
        YQLUtils.printTree(context.logger, node);

        if (node.isASTInstance(SPACE.class)) {
            node = node.getParent().getYQLNode();
        }

        if (node.isASTInstance(spaceElm.class) && node.getParent().isASTInstance(request.class)) {
            return items;
        }

        Node possibleRequestNode = node.getParent(5);
        if (possibleRequestNode != null & possibleRequestNode.isASTInstance(request.class)) {
            return List.of(CompletionUtils.constructSnippet("where", "where($0)"));
        }

        return empty;
    }
}
