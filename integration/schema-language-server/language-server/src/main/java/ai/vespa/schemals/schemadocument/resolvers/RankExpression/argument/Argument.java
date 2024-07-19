package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public interface Argument {

    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode argument);

    public boolean validateArgument(RankNode arguemnts);

    public int getStrictness();

    public String displayString();
}
