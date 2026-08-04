package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.COLON;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * A tagged label argument of the form label:&lt;query-item-label&gt;.
 */
public class TaggedLabelArgument implements Argument {

    private final String displayString;

    public TaggedLabelArgument(String displayString) {
        this.displayString = "label:" + displayString;
    }

    @Override
    public int getStrictness() {
        return 8;
    }

    @Override
    public String displayString() {
        return displayString;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        if (!node.isTaggedArgument() || !"label".equals(node.getTagKey())) {
            return false;
        }
        SchemaNode valueNode = node.getTagValueNode();
        if (valueNode == null) {
            return false;
        }
        if (RankNode.tagValueIsIdentifier(valueNode)
                || RankNode.tagValueIsString(valueNode)
                || RankNode.tagValueIsInteger(valueNode)) {
            return !node.getTagValue().isEmpty();
        }
        return false;
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        markTagKey(context, node);

        if (!validateArgument(node)) {
            SchemaNode target = node.getTagValueNode() != null ? node.getTagValueNode() : node.getSchemaNode();
            return Optional.of(new SchemaDiagnostic.Builder()
                .setRange(target.getRange())
                .setMessage("The argument must be of the form " + displayString() + ".")
                .setSeverity(DiagnosticSeverity.Error)
                .build());
        }

        SchemaNode valueNode = node.getTagValueNode();
        if (RankNode.tagValueIsIdentifier(valueNode)) {
            ArgumentUtils.modifyNodeSymbol(context, RankNode.wrap(RankNode.resolveTagValueLeaf(valueNode)),
                                           SymbolType.LABEL, SymbolStatus.BUILTIN_REFERENCE);
        }
        return Optional.empty();
    }

    private static void markTagKey(ParseContext context, RankNode node) {
        for (Node child : node.getSchemaNode()) {
            if (child.isASTInstance(COLON.class)) {
                break;
            }
            if ("label".equals(child.getText())) {
                ArgumentUtils.modifyNodeSymbol(context, RankNode.wrap(child.getSchemaNode()),
                                               SymbolType.DIMENSION, SymbolStatus.BUILTIN_REFERENCE);
                break;
            }
        }
    }
}
