package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class ExpressionArgument implements Argument {

    private String displayStr = "EXPRESSION";

    public ExpressionArgument(String displayStr) {
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
        return Optional.empty();
    }

    @Override
    public String displayString() {
        return displayStr;
    }

}
