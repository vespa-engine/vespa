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
 * Rejects the removed {@code label(name)} wrapper with a single diagnostic.
 */
public class LabelWrapperRejectionArgument implements Argument {

    @Override
    public int getStrictness() {
        return 2;
    }

    @Override
    public String displayString() {
        return "label:name";
    }

    @Override
    public boolean validateArgument(RankNode node) {
        return findLabelFunction(node).isPresent();
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        Optional<RankNode> labelFunction = findLabelFunction(node);
        if (labelFunction.isEmpty()) {
            return Optional.empty();
        }
        ArgumentUtils.modifyNodeSymbol(context, node, SymbolType.FUNCTION, SymbolStatus.BUILTIN_REFERENCE);
        for (RankNode name : labelFunction.get().getChildren()) {
            ArgumentUtils.modifyNodeSymbol(context, name, SymbolType.LABEL, SymbolStatus.BUILTIN_REFERENCE);
        }
        return Optional.of(new SchemaDiagnostic.Builder()
            .setRange(node.getRange())
            .setMessage("The argument must be of the form label:name.")
            .setSeverity(DiagnosticSeverity.Error)
            .build());
    }

    private static Optional<RankNode> findLabelFunction(RankNode node) {
        if (node.getType() != RankNodeType.EXPRESSION || node.getChildren().size() != 1) {
            return Optional.empty();
        }
        RankNode function = node.getChildren().get(0);
        if (function.getType() != RankNodeType.FEATURE
                || !function.hasSymbol()
                || !function.getSymbol().getShortIdentifier().equals("label")
                || !function.getArgumentListExists()) {
            return Optional.empty();
        }
        return Optional.of(function);
    }
}
