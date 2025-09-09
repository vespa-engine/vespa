package ai.vespa.schemals.lsp.yqlplus.completion.provider;

import java.util.Arrays;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Position;

import com.yahoo.search.grouping.request.MathFunctions;
import com.yahoo.search.grouping.request.TimeFunctions;

import ai.vespa.schemals.context.EventCompletionContext;
import ai.vespa.schemals.lsp.common.completion.CompletionProvider;
import ai.vespa.schemals.lsp.common.completion.CompletionUtils;
import ai.vespa.schemals.parser.grouping.ast.DOT;
import ai.vespa.schemals.parser.grouping.ast.MATH;
import ai.vespa.schemals.parser.grouping.ast.TIME;
import ai.vespa.schemals.parser.grouping.ast.expElm;
import ai.vespa.schemals.parser.grouping.ast.expList;
import ai.vespa.schemals.parser.grouping.ast.lbraceElm;
import ai.vespa.schemals.parser.grouping.ast.operationBody;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.Node.LanguageType;

public class GroupingExpressionProvider implements CompletionProvider {


    private final static List<String> mathFunctions = Arrays.stream(MathFunctions.Function.values())
                                                            .map(func -> func.name().toLowerCase())
                                                            .toList();

    private final static List<String> timeFunctions = Arrays.stream(TimeFunctions.Type.values())
                                                            .map(func -> func.name()
                                                                             .replaceAll("_", "")
                                                                             .toLowerCase())
                                                            .toList();

    private final static List<CompletionItem> expressionCompletions = List.of(
           CompletionUtils.constructSnippet("add", "add($0)"),
           CompletionUtils.constructSnippet("and", "and($0)"),
           CompletionUtils.constructSnippet("array", "array.at(${1:attribute}, ${2:expression})"),
           CompletionUtils.constructSnippet("attribute", "attribute($0)"),
           CompletionUtils.constructSnippet("avg", "avg($0)"),
           CompletionUtils.constructSnippet("cat", "cat($0)"),
           CompletionUtils.constructSnippet("count", "count()$0"),
           CompletionUtils.constructSnippet("debugwait", "debugwait(${1:expr}, ${2:waittime}, ${3:busy})"),
           CompletionUtils.constructSnippet("div", "div($0)"),
           CompletionUtils.constructSnippet("docidnsspecific", "docidnsspecific()"),
           CompletionUtils.constructSnippet("fixedwidth", "fixedwidth(${1:expr}, ${2:width})"),
           CompletionUtils.constructSnippet("interpolatedlookup", "interpolatedlookup(${1:attribute}, ${2:expression})"),
           CompletionUtils.constructSnippet("math", "math.${1|" + String.join(",", mathFunctions) + "|}($0)"),
           CompletionUtils.constructSnippet("max", "max($0)"),
           CompletionUtils.constructSnippet("md5", "md5($0)"),
           CompletionUtils.constructSnippet("min", "min($0)"),
           CompletionUtils.constructSnippet("mod", "mod($0)"),
           CompletionUtils.constructSnippet("mul", "mul($0)"),
           CompletionUtils.constructSnippet("neg", "neg($0)"),
           CompletionUtils.constructSnippet("normalizesubject", "normalizesubject($0)"),
           CompletionUtils.constructSnippet("now", "now()"),
           CompletionUtils.constructSnippet("or", "or($0)"),
           CompletionUtils.constructSnippet("predefined", "predefined(${1:expr}, ${2:bucket})"),
           CompletionUtils.constructSnippet("relevance", "relevance()"),
           CompletionUtils.constructSnippet("reverse", "reverse($0)"),
           CompletionUtils.constructSnippet("quantiles", "quantiles([${1:quantiles}], ${2:expr})"),
           CompletionUtils.constructSnippet("size", "size($0)"),
           CompletionUtils.constructSnippet("sort", "sort($0)"),
           CompletionUtils.constructSnippet("stddev", "stddev($0)"),
           CompletionUtils.constructSnippet("strcat", "strcat($0)"),
           CompletionUtils.constructSnippet("strlen", "strlen($0)"),
           CompletionUtils.constructSnippet("sub", "sub($0)"),
           CompletionUtils.constructSnippet("sum", "sum($0)"),
           CompletionUtils.constructSnippet("summary", "summary($0)"),
           CompletionUtils.constructSnippet("time", "time.${1|" + String.join(",", timeFunctions) + "|}($0)"),
           CompletionUtils.constructSnippet("todouble", "todouble($0)"),
           CompletionUtils.constructSnippet("tolong", "tolong($0)"),
           CompletionUtils.constructSnippet("toraw", "toraw($0)"),
           CompletionUtils.constructSnippet("tostring", "tostring($0)"),
           CompletionUtils.constructSnippet("uca", "uca(${1:expr}, ${2:locale})"),
           CompletionUtils.constructSnippet("xorbit", "xorbit(${1:expr}, ${2:width})"),
           CompletionUtils.constructSnippet("xor", "xor($0)"),
           CompletionUtils.constructSnippet("zcurve", "zcurve.${1|x,y|}($0)")
    );

    private boolean expressionExpected(Node node) {
        Node ptr = node;

        while (ptr != null) {
            if (ptr.isASTInstance(expElm.class)) return true;
            if (ptr.isASTInstance(lbraceElm.class)) {
                if (ptr.getNextSibling() != null && (
                        ptr.getNextSibling().isASTInstance(expList.class)
                    ||  ptr.getNextSibling().isASTInstance(expElm.class)
                )) {
                    return true;
                }
            }
            if (ptr.isASTInstance(operationBody.class)) return false;

            ptr = ptr.getParent();
        }
        return false;
    }

    @Override
    public List<CompletionItem> getCompletionItems(EventCompletionContext context) {
        Position prevPos = CSTUtils.subtractOneChar(context.position);
        Node last = CSTUtils.getLastCleanNode(context.document.getRootYQLNode(), prevPos);

        if (last == null) {
            return List.of();
        }

        if (!last.isYQLNode()) {
            throw new IllegalArgumentException("Unexpected node type, expected a YQLNode");
        }

        if (last.getLanguageType() == LanguageType.YQLPlus) {
            return List.of();
        }

        if (!expressionExpected(last)) {
            return List.of();
        }

        if (last.isASTInstance(DOT.class) && last.getPreviousSibling() != null) {
            Node sibling = last.getPreviousSibling();
            if (sibling.isASTInstance(MATH.class)) {
                return mathFunctions.stream()
                    .map(func -> CompletionUtils.constructSnippet(func, func + "($0)"))
                    .toList();
            } else if (sibling.isASTInstance(TIME.class)) {
                return timeFunctions.stream()
                    .map(func -> CompletionUtils.constructSnippet(func, func + "($0)"))
                    .toList();
            }
        }

        return expressionCompletions;
    }
}
