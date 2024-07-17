package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.parser.rankingexpression.ast.INTEGER;
import ai.vespa.schemals.tree.SchemaNode;

public class IntegerFunction implements FunctionHandler {


    public List<Diagnostic> handleArgumentList(ParseContext context, SchemaNode node, List<SchemaNode> arguments) {
        List<Diagnostic> diagnositcs = new ArrayList<>();

        if (arguments.size() != 1) {
            diagnositcs.add(new Diagnostic(node.getRange(), "The function '" + node.getText() + "', takes exacly one argument 'n'", DiagnosticSeverity.Error, ""));
            return diagnositcs;
        }

        SchemaNode leaf = arguments.get(0);

        while (leaf.size() > 0) {
            if (leaf.hasSymbol()) {
                Symbol symbol = leaf.getSymbol();
                if (symbol.getStatus() == SymbolStatus.REFERENCE) {
                    context.schemaIndex().deleteSymbolReference(symbol);
                }
                leaf.removeSymbol();
            }
            leaf = leaf.get(0);
        }

        if (!leaf.isASTInstance(INTEGER.class)) {
            diagnositcs.add(new Diagnostic(leaf.getRange(), "Argument of function '" + node.getText() + "' must be an INTEGER.", DiagnosticSeverity.Error, ""));
        }

        return diagnositcs;
    }
}
