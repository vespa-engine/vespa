package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class StringArgument implements Argument {

    private String displayStr = "STRING";

    public StringArgument(String displayStr) {
        this.displayStr = displayStr;
    }

    @Override
    public int getStrictness() {
        return 1;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        return true;
    }
    
    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {

        SchemaNode leaf = node.getSchemaNode();

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

        return Optional.empty();
    }

    public String displayString() {
        return displayStr;
    }
}
