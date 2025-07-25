package ai.vespa.schemals.lsp.yqlplus.completion.provider;

import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.common.completion.CompletionProvider;
import ai.vespa.schemals.lsp.common.completion.CompletionUtils;
import ai.vespa.schemals.parser.grouping.ast.expElm;
import ai.vespa.schemals.parser.grouping.ast.filterExp;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;

public class GroupingFilterProvider implements CompletionProvider {

    @Override
    public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        Node node = CSTUtils.getLastCleanNode(context.document.getRootYQLNode(), context.position);

        if (node == null) {
            return List.of();
        }

        // Match: inside filter, but not expression
        if (CSTUtils.findASTClassAncestor(node, expElm.class) != null) return List.of();
        if (CSTUtils.findASTClassAncestor(node, filterExp.class) == null) return List.of();

        return List.of(
            CompletionUtils.constructSnippet("regex", "regex(\"$1\", $2)"),
            CompletionUtils.constructSnippet("and", "and($0)"),
            CompletionUtils.constructSnippet("or", "or($0)"),
            CompletionUtils.constructSnippet("not", "not($0)")
        );
    }
}
