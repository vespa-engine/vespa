package ai.vespa.schemals.lsp.yqlplus.completion.provider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.common.completion.CompletionProvider;
import ai.vespa.schemals.lsp.common.completion.CompletionUtils;
import ai.vespa.schemals.parser.grouping.ast.allOperation;
import ai.vespa.schemals.parser.grouping.ast.eachOperation;
import ai.vespa.schemals.parser.grouping.ast.operation;
import ai.vespa.schemals.parser.grouping.ast.operationBody;
import ai.vespa.schemals.parser.grouping.ast.rbraceElm;
import ai.vespa.schemals.parser.grouping.ast.spaceElm;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.YQLNode;
import ai.vespa.schemals.tree.Node.LanguageType;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class GroupOperationCompletion implements CompletionProvider {

    private final static List<CompletionItem> completionItems = new ArrayList<>() {{
        add(CompletionUtils.constructSnippet("accuracy", "accuracy(${1:number})$0"));
        add(CompletionUtils.constructSnippet("alias", "alias(${1:identifier}, ${2:expression})$0"));
        add(CompletionUtils.constructSnippet("hint", "hint(${1:identifier})$0"));
        add(CompletionUtils.constructSnippet("max", "max(${1:number})$0"));
        add(CompletionUtils.constructSnippet("order", "order($1)$0"));
        add(CompletionUtils.constructSnippet("output", "output($1)$0"));
        add(CompletionUtils.constructSnippet("precision", "precision(${1:number})$0"));
        add(CompletionUtils.constructSnippet("where", "where($1)$0"));

        add(CompletionUtils.constructSnippet("all", "all($0)"));
        add(CompletionUtils.constructSnippet("each", "each($0)"));
    }};

    @Override
    public List<CompletionItem> getCompletionItems(EventCompletionContext context) {

        // TODO: An operation consists of this:
        //
        // [GROUP()] <many different functiosn>* <all() | each()>*

        List<CompletionItem> ret = new ArrayList<>();

        Node last = CSTUtils.getLastCleanNode(context.document.getRootYQLNode(), CSTUtils.subtractOneChar(context.position));

        if (last == null) {
            return ret;
        }

        if (!last.isYQLNode()) {
            throw new IllegalArgumentException("Unexpected node type, expected a YQLNode");
        }

        if (last.getLanguageType() == LanguageType.YQLPlus) {
            return ret;
        }

        YQLNode node = last.getYQLNode();
        if (!node.isASTInstance(rbraceElm.class)) {
            return ret;
        }

        context.logger.info("GROUPING Completion:");
        YQLUtils.printTree(context.logger, node);

        context.logger.info("GRAND PARENT");
        YQLUtils.printTree(context.logger, node.getParent().getParent());

        if (!node.isASTInstance(rbraceElm.class)) {
            return List.of();
        }

        YQLNode parent = node.getParent().getYQLNode();
        if (!parent.isASTInstance(allOperation.class) && !parent.isASTInstance(eachOperation.class)) {
            return List.of();
        }

        Node previousSibling = node.getPreviousSibling();
        if (previousSibling != null && previousSibling.isASTInstance(operationBody.class)) {
            if (previousSibling.size() == 0) {
                ret.add(CompletionUtils.constructSnippet("group", "group($1)$0"));
            } else {
                Node lastOperationBodyChild = previousSibling.get(previousSibling.size() - 1);
                if (lastOperationBodyChild.isASTInstance(operation.class)) {
                    return List.of();
                }
            }
        }

        ret.addAll(completionItems);

        return ret;
    }
}
