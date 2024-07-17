package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public interface FunctionHandler {

    public List<Diagnostic> handleArgumentList(ParseContext context, SchemaNode node, List<SchemaNode> arguments, Optional<SchemaNode> property);
}
