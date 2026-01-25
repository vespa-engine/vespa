package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.rankingexpression.ast.IDENTIFIER;
import ai.vespa.schemals.parser.rankingexpression.ast.args;
import ai.vespa.schemals.parser.rankingexpression.ast.expression;
import ai.vespa.schemals.parser.rankingexpression.ast.feature;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * An argument that must be an identifier bound by an enclosing foreach loop.
 *
 * In Vespa ranking expressions, foreach loops bind a variable that can be used
 * in nested expressions like term(N) where N is the loop variable.
 */
public class IdentifierArgument implements Argument {

    private String displayStr;

    public IdentifierArgument(String displayStr) {
        this.displayStr = displayStr;
    }

    public IdentifierArgument() {
        this("identifier");
    }

    @Override
    public int getStrictness() {
        return 5;  // Between IntegerArgument (6) and StringArgument (2)
    }

    @Override
    public boolean validateArgument(RankNode node) {
        Node leaf = node.getSchemaNode().findFirstLeaf();
        return leaf.isASTInstance(IDENTIFIER.class);
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        ArgumentUtils.removeNodeSymbols(context, node);

        Node leaf = node.getSchemaNode().findFirstLeaf();
        String identifierName = leaf.getText();

        // Find enclosing foreach and validate that this identifier is bound by it
        Optional<String> foreachVariable = findEnclosingForeachVariable(node.getSchemaNode());

        if (foreachVariable.isEmpty() || !foreachVariable.get().equals(identifierName)) {
            return Optional.of(new SchemaDiagnostic.Builder()
                .setRange(leaf.getRange())
                .setMessage("Identifier '" + identifierName + "' is unbound.")
                .setSeverity(DiagnosticSeverity.Error)
                .build());
        }

        return Optional.empty();
    }

    /**
     * Finds the variable name bound by an enclosing foreach loop.
     *
     * foreach has the signature: foreach(dimension, variable, feature, condition, operation)
     * The second argument is the variable name.
     */
    private Optional<String> findEnclosingForeachVariable(Node node) {
        // Walk up to find a feature node that is a foreach call
        Node current = node.getParent();
        while (current != null) {
            if (current.isASTInstance(feature.class)) {
                // Check if this feature is a foreach
                if (current.size() > 0) {
                    Node identifierNode = current.get(0);
                    String featureName = identifierNode.getText();

                    if ("foreach".equals(featureName)) {
                        // Find the args node and get the second argument (variable name)
                        return extractForeachVariable(current);
                    }
                }
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    /**
     * Extracts the variable name from the second argument of a foreach feature node.
     */
    private Optional<String> extractForeachVariable(Node foreachNode) {
        // Find the args child
        for (int i = 0; i < foreachNode.size(); i++) {
            Node child = foreachNode.get(i);
            if (child.isASTInstance(args.class)) {
                // The second child of args is the variable (after the dimension keyword)
                // args contains: expression, expression, expression, ...
                // Position 1 is the variable name
                int expressionIndex = 0;
                for (int j = 0; j < child.size(); j++) {
                    Node argChild = child.get(j);
                    // Skip non-expression nodes (commas, etc.)
                    if (argChild.isASTInstance(expression.class)) {
                        if (expressionIndex == 1) {
                            // This is the variable argument
                            Node leaf = argChild.findFirstLeaf();
                            if (leaf != null) {
                                return Optional.of(leaf.getText());
                            }
                        }
                        expressionIndex++;
                    }
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public String displayString() {
        return displayStr;
    }
}
