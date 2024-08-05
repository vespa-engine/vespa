package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class DimensionArgument implements Argument {

    private String argument;

    public DimensionArgument(String argument) {
        this.argument = argument;
    }

    public String displayString() {
        return argument;
    }

    public int getStrictness() {
        return 8;
    }

    public boolean validateArgument(RankNode node) {
        return node.getSchemaNode().getText().equals(argument);
    }

    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {

        if (!validateArgument(node)) {
            return Optional.empty();
        }

        Symbol symbol = node.getSymbol();

        if (symbol == null) {
            return Optional.empty();
        }

        if (symbol.getStatus() == SymbolStatus.REFERENCE) {
            context.schemaIndex().deleteSymbolReference(symbol);
        }

        symbol.setType(SymbolType.DIMENSION);
        symbol.setStatus(SymbolStatus.BUILTIN_REFERENCE);

        return Optional.empty();
    }

}
