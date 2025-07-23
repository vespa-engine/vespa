package ai.vespa.schemals.lsp.yqlplus.completion.provider;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.common.completion.CompletionProvider;
import ai.vespa.schemals.lsp.common.completion.CompletionUtils;
import ai.vespa.schemals.parser.grouping.ast.LBRACE;
import ai.vespa.schemals.parser.grouping.ast.RBRACE;
import ai.vespa.schemals.parser.grouping.ast.SPACE;
import ai.vespa.schemals.parser.grouping.ast.lbraceElm;
import ai.vespa.schemals.parser.grouping.ast.operation;
import ai.vespa.schemals.parser.grouping.ast.operationBody;
import ai.vespa.schemals.parser.grouping.ast.rbraceElm;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.Node.LanguageType;

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
        add(CompletionUtils.constructSnippet("filter", "filter(regex(\"$1\", $2))$0"));
    }};

    private final static List<CompletionItem> allEachCompletionItems = new ArrayList<>() {{
        add(CompletionUtils.constructSnippet("all", "all($1)$0"));
        add(CompletionUtils.constructSnippet("each", "each($1)$0"));
    }};

    private boolean isAllOrEachElement(Node node) {
        return node.getParent().isASTInstance(operation.class);
    }

    private Optional<Node> findOperationBody(Node last) {

        if (last.isASTInstance(LBRACE.class)) {
            Node grandParent = last.getParent(2);
            if (isAllOrEachElement(grandParent)) {
                return Optional.of(last.getParent().getNextSibling());
            }
        }

        if (last.isASTInstance(SPACE.class)) {
            Node grandParent = last.getParent(2);

            if (grandParent.isASTInstance(lbraceElm.class)) {
                if (isAllOrEachElement(grandParent.getParent())) {
                    return Optional.of(grandParent.getNextSibling());
                }
            } else if (grandParent.isASTInstance(rbraceElm.class)) {
                if (grandParent.getParent().isASTInstance(operationBody.class)) {
                    return Optional.of(grandParent.getParent());
                }

                if (isAllOrEachElement(grandParent.getParent())) {
                    Node possiblyOperationBody = grandParent.getParent(3);
                    if (possiblyOperationBody.isASTInstance(operationBody.class)) {
                        return Optional.of(possiblyOperationBody);
                    }
                }
            }
        }

        if (last.isASTInstance(RBRACE.class)) {
            Node grandParent = last.getParent(2);
            if (grandParent.isASTInstance(operationBody.class)) {
                return Optional.of(grandParent);
            }

            if (isAllOrEachElement(grandParent)) {
                Node possiblyOperationBody = grandParent.getParent(2);
                if (possiblyOperationBody.isASTInstance(operationBody.class)) {
                    return Optional.of(possiblyOperationBody);
                }
            }
        }

        if (last.isASTInstance(rbraceElm.class)) {
            Node sibling = last.getPreviousSibling();
            if (sibling != null && sibling.isASTInstance(operationBody.class)) {
                return Optional.of(sibling);
            }
        }

        return Optional.empty();
    }

    private List<Node> getBodyElements(Node operationBody) {
        List<Node> ret = new ArrayList<>();

        for (Node child : operationBody) {
            if (child.isLeaf() || child.isASTInstance(operation.class)) {
                ret.add(child);
            }
        }

        return ret;
    }

    private Optional<Integer> getPreviousNode(Position position, List<Node> nodes) {

        for (int i = 0; i < nodes.size(); i++) {
            if (CSTUtils.positionInRange(nodes.get(i).getRange(), position)) {
                return Optional.empty();
            }
            if (CSTUtils.positionLT(position, nodes.get(i).getRange().getEnd())) {
                return Optional.of(i);
            }
        }

        return Optional.of(nodes.size());
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventCompletionContext context) {

        // This is the syntax of the content of a operationBody
        // [GROUP()] <many different functions>* <all() | each()>*

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

        Optional<Node> operationBody = findOperationBody(last);

        if (operationBody.isEmpty()) {
            return ret;
        }

        List<Node> operationNodes = getBodyElements(operationBody.get());
        Optional<Integer> indexOfCursor = getPreviousNode(context.position, operationNodes);

        // Cursor is inside a token
        if (indexOfCursor.isEmpty()) {
            return ret;
        }

        if (indexOfCursor.get() == 0) {
            ret.add(CompletionUtils.constructSnippet("group", "group($1)$0"));
        } else {
            Node previousNode = operationNodes.get(indexOfCursor.get() - 1);
            if (previousNode.isASTInstance(operation.class)) {
                return allEachCompletionItems;
            }
        }

        ret.addAll(completionItems);
        ret.addAll(allEachCompletionItems);

        return ret;
    }
}
