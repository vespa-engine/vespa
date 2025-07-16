package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * An argument that has to be a specific word.
 * Equivalent to an {@link ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument.EnumArgument} with only one element.
 */
public class KeywordArgument implements Argument {

    private String argument;
    private String displayStr;

    public KeywordArgument(String argument, String displayStr) {
        this.argument = argument;
        this.displayStr = displayStr;
    }

    public KeywordArgument(String argument) {
        this(argument, argument);
    }
    
    @Override
    public String displayString() {
        return displayStr;
    }

    public String getArgument() { return argument; }

    @Override
    public int getStrictness() {
        return 8;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        return node.getSchemaNode().getText().equals(argument);
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {

        if (!validateArgument(node)) {
            return Optional.empty();
        }

        // TODO: Why is it DIMENSION?
        ArgumentUtils.modifyNodeSymbol(context, node, SymbolType.DIMENSION, SymbolStatus.BUILTIN_REFERENCE);

        return Optional.empty();
    }

}
