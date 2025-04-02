package ai.vespa.schemals.lsp.yqlplus.completion.provider;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import ai.vespa.schemals.common.ClientLogger;
import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.common.completion.CompletionProvider;
import ai.vespa.schemals.lsp.common.completion.CompletionUtils;
import ai.vespa.schemals.parser.yqlplus.ast.limit_fun;
import ai.vespa.schemals.parser.yqlplus.ast.offset_fun;
import ai.vespa.schemals.parser.yqlplus.ast.orderby_fields;
import ai.vespa.schemals.parser.yqlplus.ast.orderby_fun;
import ai.vespa.schemals.parser.yqlplus.ast.query_statement;
import ai.vespa.schemals.parser.yqlplus.ast.select_field_spec;
import ai.vespa.schemals.parser.yqlplus.ast.select_source;
import ai.vespa.schemals.parser.yqlplus.ast.select_source_from;
import ai.vespa.schemals.parser.yqlplus.ast.select_statement;
import ai.vespa.schemals.parser.yqlplus.ast.timeout_fun;
import ai.vespa.schemals.parser.yqlplus.ast.where_fun;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.YQLNode;
import ai.vespa.schemals.tree.Node.LanguageType;
import ai.vespa.schemals.tree.YQL.YQLUtils;

public class RootCompletion implements CompletionProvider {

    private record StatementPair(
        Class<?> parentClass,
        String statement
    ) {}

    private final List<StatementPair> statementOrder = new ArrayList<StatementPair>() {{
        add(new StatementPair(select_statement.class, "select"));
        add(new StatementPair(select_source.class, "from"));
        add(new StatementPair(where_fun.class, "where"));
        add(new StatementPair(orderby_fun.class, "order by"));
        add(new StatementPair(limit_fun.class, "limit"));
        add(new StatementPair(offset_fun.class, "offset"));
        add(new StatementPair(timeout_fun.class, "timeout"));
    }};

    private int statementOrderListContains(Class<?> cls) {
        for (int i = 0; i < statementOrder.size(); i++) {
            if (statementOrder.get(i).parentClass == cls) {
                return i;
            }
        }

        return -1;
    }

    private List<String> getLastestStatement(YQLNode node) {
        YQLNode iterator = node;
        YQLNode lastIterator = node;
        while (!iterator.isASTInstance(query_statement.class) && iterator != null) {

            int hitIndex = statementOrderListContains(iterator.getASTClass());
            if (hitIndex != -1 && lastIterator != iterator.get(0)) {
                List<String>ret = new ArrayList<>();

                for (int i = hitIndex + 1; i < statementOrder.size(); i++) {
                    ret.add(statementOrder.get(i).statement());
                }

                return ret;
            }

            lastIterator = iterator;
            if (iterator.getParent() == null) {
                iterator = null;
            } else {
                iterator = iterator.getParent().getYQLNode();
            }
        }

        return List.of();
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        List<CompletionItem> ret = new ArrayList<>();

        Position searchPos = context.startOfWord();
        if (searchPos == null)searchPos = context.position;

        Node last = CSTUtils.getLastCleanNode(context.document.getRootYQLNode(), searchPos);

        if (last == null) {
            return ret;
        }

        if (!last.isYQLNode()) {
            throw new IllegalArgumentException("Unexpected node type, expected a YQLNode");
        }

        if (last.getLanguageType() == LanguageType.GROUPING) {
            return ret;
        }

        YQLNode node = last.getYQLNode();

        if (node.getLanguageType() == LanguageType.CUSTOM && node.getText() == "|") {
            return ret;
        }

        context.logger.info("YQL comp:");
        YQLUtils.printTree(context.logger, node);

        if (node.isASTInstance(YQLNode.class)) {
            ret.add(CompletionUtils.constructSnippet("select", "select ${1:*} from $2 where $0"));
            return ret;
        }

        List<String> completionStrings = getLastestStatement(node);

        for (String completionStr : completionStrings) {
            ret.add(CompletionUtils.constructSnippet(completionStr, completionStr + " $0"));
        }




        return ret;
    }
}
