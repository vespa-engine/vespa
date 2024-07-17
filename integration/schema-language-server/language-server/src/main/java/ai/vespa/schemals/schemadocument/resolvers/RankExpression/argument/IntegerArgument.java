package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.parser.rankingexpression.ast.INTEGER;
import ai.vespa.schemals.tree.SchemaNode;

public class IntegerArgument implements Argument {
    

    public List<Diagnostic> verifyArgument(ParseContext context, SchemaNode argument) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        SchemaNode leaf = argument;

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
            diagnostics.add(new Diagnostic(leaf.getRange(), "Argument of function must be an INTEGER.", DiagnosticSeverity.Error, ""));
        }


        return diagnostics;
    }
}
