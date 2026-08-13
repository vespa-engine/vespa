package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode.RankNodeType;

/**
 * Rejects a bare second argument where {@code label: name} is required.
 */
public class BareLabelRejectionArgument implements Argument {

    private final String displayString;

    public BareLabelRejectionArgument(String displayString) {
        this.displayString = "label:" + displayString;
    }

    @Override
    public int getStrictness() {
        return 2;
    }

    @Override
    public String displayString() {
        return displayString;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        if (node.isTaggedArgument()) {
            return false;
        }
        if (node.getType() != RankNodeType.EXPRESSION || node.getChildren().size() != 1) {
            return false;
        }
        RankNode feature = node.getChildren().get(0);
        return feature.getType() == RankNodeType.FEATURE
                && feature.hasSymbol()
                && !feature.getArgumentListExists()
                && feature.getProperty().isEmpty();
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        if (!validateArgument(node)) {
            return Optional.empty();
        }
        ArgumentUtils.modifyNodeSymbol(context, node.getChildren().get(0), SymbolType.LABEL, SymbolStatus.BUILTIN_REFERENCE);
        return Optional.of(new SchemaDiagnostic.Builder()
            .setRange(node.getRange())
            .setMessage("The argument must be of the form " + displayString() + ".")
            .setSeverity(DiagnosticSeverity.Error)
            .build());
    }
}
