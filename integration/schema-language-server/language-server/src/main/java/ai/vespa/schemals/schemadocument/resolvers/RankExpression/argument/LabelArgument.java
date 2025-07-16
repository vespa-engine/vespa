package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * A 'label' argument, which can be any string, but is used for 
 * referring to a term or operator during ranking, so it will get a different semantic token
 * coloring.
 */
public class LabelArgument implements Argument {

    private String displayString;

    public String displayString() {
        return displayString;
    }

    public LabelArgument() {
        this.displayString = "label";
    }

    public LabelArgument(String displayString) {
        this.displayString = displayString;
    }

    public int getStrictness() {
        return 2;
    }

    public boolean validateArgument(RankNode Node) {
        return true;
    }

    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        ArgumentUtils.modifyNodeSymbol(context, node, SymbolType.LABEL, SymbolStatus.BUILTIN_REFERENCE);
        return Optional.empty();
    }


}
