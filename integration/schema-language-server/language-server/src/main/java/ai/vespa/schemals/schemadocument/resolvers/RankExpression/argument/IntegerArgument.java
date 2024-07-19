package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.parser.rankingexpression.ast.INTEGER;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class IntegerArgument implements Argument {

    @Override
    public int getStrictness() {
        return 6;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        SchemaNode leaf = node.getSchemaNode().findFirstLeaf();
        return leaf.isASTInstance(INTEGER.class);
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode argument) {

        SchemaNode leaf = argument.getSchemaNode();

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
            return Optional.of(new Diagnostic(leaf.getRange(), "Argument of function must be an INTEGER.", DiagnosticSeverity.Error, ""));
        }


        return Optional.empty();
    }

    @Override
    public String displayString() {
        return "n";
    }
}
