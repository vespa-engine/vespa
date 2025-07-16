package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * Functionally similar to a {@link ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.StringArgument},
 * but should refer to a vector sent with the query. 
 * This class exists in case we want to do more checks later.
 */
public class VectorArgument implements Argument {

	@Override
	public Optional<Diagnostic> parseArgument(ParseContext context, RankNode argument) {
        return Optional.empty();
	}

	@Override
	public boolean validateArgument(RankNode arguemnts) {
        return true;
	}

	@Override
	public int getStrictness() {
        return 1;
	}

	@Override
	public String displayString() {
        return "vector";
	}

    
}
