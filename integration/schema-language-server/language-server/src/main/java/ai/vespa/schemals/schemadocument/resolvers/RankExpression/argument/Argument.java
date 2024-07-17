package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public interface Argument {

    public List<Diagnostic> verifyArgument(ParseContext context, SchemaNode argument);

}
