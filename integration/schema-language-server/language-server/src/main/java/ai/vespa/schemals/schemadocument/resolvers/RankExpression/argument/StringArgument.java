package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * An argument that can be any string.
 */
public class StringArgument implements Argument {

    private String displayStr = "STRING";

    public StringArgument(String displayStr) {
        this.displayStr = displayStr;
    }

    @Override
    public int getStrictness() {
        return 2;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        return true;
    }
    
    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        ArgumentUtils.removeNodeSymbols(context, node);

        return Optional.empty();
    }

    public String displayString() {
        return displayStr;
    }
}
