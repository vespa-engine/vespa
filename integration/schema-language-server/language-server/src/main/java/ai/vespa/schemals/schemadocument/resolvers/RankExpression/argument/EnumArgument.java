package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.List;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * An argument that only can be one of a list of valid strings.
 * For example the 'operation' argument of the 'foreach' feature.
 */
public class EnumArgument implements Argument {
    String displayString;
    List<String> validArguments;

    public EnumArgument(String displayString, List<String> validArguments) {
        this.validArguments = List.copyOf(validArguments);
        this.displayString = displayString;
    }

    public List<String> getValidArguments() { return validArguments; }

	@Override
	public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        if (!validateArgument(node)) return Optional.empty();

        ArgumentUtils.modifyNodeSymbol(context, node, SymbolType.DIMENSION, SymbolStatus.BUILTIN_REFERENCE);

        return Optional.empty();
	}

	@Override
	public boolean validateArgument(RankNode node) {
        return validArguments.contains(node.getSchemaNode().getText());
	}

	@Override
	public int getStrictness() {
        return 5;
	}

	@Override
	public String displayString() {
        return displayString;
	}

    
}
