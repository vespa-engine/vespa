package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.rankingexpression.ast.INTEGER;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * An argument that has to be an integer literal.
 */
public class IntegerArgument implements Argument {

    private String displayStr;

    public IntegerArgument(String displayStr) {
        this.displayStr = displayStr;
    }

    public IntegerArgument() {
        this("n");
    }

    @Override
    public int getStrictness() {
        return 6;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        Node leaf = node.getSchemaNode().findFirstLeaf();
        return leaf.isASTInstance(INTEGER.class);
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {

        Node leaf = ArgumentUtils.removeNodeSymbols(context, node);

        if (!leaf.isASTInstance(INTEGER.class)) {
            return Optional.of(new SchemaDiagnostic.Builder()
                .setRange(leaf.getRange())
                .setMessage("Argument of function must be an INTEGER.")
                .setSeverity(DiagnosticSeverity.Error)
                .build());
        }

        return Optional.empty();
    }

    @Override
    public String displayString() {
        return displayStr;
    }
}
