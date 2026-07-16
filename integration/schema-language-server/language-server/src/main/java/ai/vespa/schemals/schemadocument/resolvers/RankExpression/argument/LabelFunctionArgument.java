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
 * An argument of the form label(&lt;name&gt;), naming the set of query items
 * annotated with the given label. Exactly one name and no output suffix are
 * accepted, mirroring the C++ side (util::parse_label_argument).
 */
public class LabelFunctionArgument implements Argument {

    private String displayString;

    public LabelFunctionArgument() {
        this("name");
    }

    public LabelFunctionArgument(String nameDisplayString) {
        this.displayString = "label(" + nameDisplayString + ")";
    }

    @Override
    public int getStrictness() {
        return 2;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        Optional<RankNode> labelFunction = findLabelFunction(node);
        return labelFunction.isPresent() && isValid(labelFunction.get());
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        Optional<RankNode> labelFunction = findLabelFunction(node);
        if (labelFunction.isPresent()) {
            // the label() wrapper is not a rank feature itself; mark its symbols (even when the
            // shape is rejected below) so later passes do not try to resolve them
            ArgumentUtils.modifyNodeSymbol(context, node, SymbolType.FUNCTION, SymbolStatus.BUILTIN_REFERENCE);
            for (RankNode name : labelFunction.get().getChildren()) {
                ArgumentUtils.modifyNodeSymbol(context, name, SymbolType.LABEL, SymbolStatus.BUILTIN_REFERENCE);
            }
            if (isValid(labelFunction.get())) {
                return Optional.empty();
            }
        }
        return Optional.of(new SchemaDiagnostic.Builder()
            .setRange(node.getRange())
            .setMessage("The argument must be of the form " + displayString + ".")
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

    private static boolean isValid(RankNode labelFunction) {
        return labelFunction.getChildren().size() == 1 && labelFunction.getProperty().isEmpty();
    }

    @Override
    public String displayString() {
        return displayString;
    }

}
