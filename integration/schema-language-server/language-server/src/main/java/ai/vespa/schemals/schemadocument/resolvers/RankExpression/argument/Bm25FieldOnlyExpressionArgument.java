package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.rankingexpression.RankNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode.RankNodeType;

/**
 * The first argument to {@code elementwise} must be a one-argument {@code bm25(field)} call.
 */
public class Bm25FieldOnlyExpressionArgument implements Argument {

    private final String displayString;

    public Bm25FieldOnlyExpressionArgument(String displayString) {
        this.displayString = displayString;
    }

    @Override
    public int getStrictness() {
        return 5;
    }

    @Override
    public String displayString() {
        return displayString;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        return findInnerBm25(node).map(Bm25FieldOnlyExpressionArgument::isFieldOnlyBm25).orElse(false);
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        Optional<RankNode> bm25 = findInnerBm25(node);
        if (bm25.isPresent() && !isFieldOnlyBm25(bm25.get())) {
            return Optional.of(new SchemaDiagnostic.Builder()
                .setRange(bm25.get().getRange())
                .setMessage("The inner bm25 call must be of the form bm25(field).")
                .setSeverity(DiagnosticSeverity.Error)
                .build());
        }
        return Optional.empty();
    }

    private static Optional<RankNode> findInnerBm25(RankNode node) {
        if (node.getType() == RankNodeType.FEATURE && isBm25(node)) {
            return Optional.of(node);
        }
        if (node.getType() == RankNodeType.EXPRESSION && node.getChildren().size() == 1) {
            RankNode child = node.getChildren().get(0);
            if (child.getType() == RankNodeType.FEATURE && isBm25(child)) {
                return Optional.of(child);
            }
        }
        return Optional.empty();
    }

    private static boolean isBm25(RankNode node) {
        return node.hasSymbol() && "bm25".equals(node.getSymbol().getShortIdentifier());
    }

    private static boolean isFieldOnlyBm25(RankNode bm25) {
        if (!bm25.getArgumentListExists()) {
            return false;
        }
        List<RankNode> args = bm25.getChildren();
        return args.size() == 1 && !args.get(0).isTaggedArgument();
    }
}
