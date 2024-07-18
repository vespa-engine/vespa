package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public interface Argument {

    public Optional<Diagnostic> parseArgument(ParseContext context, SchemaNode argument);

    public boolean validateArgument(SchemaNode arguemnts);

    public int getStrictness();

    public String displayString();
}
