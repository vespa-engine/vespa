package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * An argument in a {@link ai.vespa.schemals.schemadocument.resolvers.RankExpression.FunctionSignature}.
 */
public interface Argument {

    // Apply processing to the argument after a match. 
    // Mostly used for deleting symbol references or changing symbol types.
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode argument);

    // Return true if the argument CST node matches this type of argument.
    public boolean validateArgument(RankNode argument);

    // How much weight this argument type should have in a matching context. Higher means higher precedence when 
    // trying to find a matching function signature.
    public int getStrictness();

    // String displayed in completion. Should match wording in docs.
    public String displayString();
}
