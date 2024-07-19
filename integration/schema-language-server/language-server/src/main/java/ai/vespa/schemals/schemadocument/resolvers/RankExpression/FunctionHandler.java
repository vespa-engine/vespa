package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public interface FunctionHandler {

    public List<Diagnostic> handleArgumentList(ParseContext context, RankNode node);
}
