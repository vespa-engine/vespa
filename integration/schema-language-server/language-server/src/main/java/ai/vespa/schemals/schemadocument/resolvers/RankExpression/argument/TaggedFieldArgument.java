package ai.vespa.schemals.schemadocument.resolvers.RankExpression.argument;

import java.util.EnumSet;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.FieldIndex.IndexingType;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.rankingexpression.ast.COLON;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

/**
 * A tagged field argument of the form field:&lt;index-field&gt;.
 */
public class TaggedFieldArgument extends FieldArgument {

    public TaggedFieldArgument(String displayStr) {
        super(FieldType.STRING, EnumSet.of(IndexingType.INDEX), displayStr);
    }

    @Override
    public String displayString() {
        return "field:" + super.displayString();
    }

    @Override
    public int getStrictness() {
        return 8;
    }

    @Override
    public boolean validateArgument(RankNode node) {
        if (!node.isTaggedArgument() || !"field".equals(node.getTagKey())) {
            return false;
        }
        SchemaNode valueNode = node.getTagValueNode();
        if (valueNode == null) {
            return false;
        }
        if (RankNode.tagValueIsIdentifier(valueNode) || RankNode.tagValueIsString(valueNode)) {
            return !RankNode.findTagValueText(valueNode).isEmpty();
        }
        return false;
    }

    @Override
    public Optional<Diagnostic> parseArgument(ParseContext context, RankNode node) {
        markTagKey(context, node);

        SchemaNode valueNode = node.getTagValueNode();
        if (!validateArgument(node)) {
            SchemaNode target = valueNode != null ? valueNode : node.getSchemaNode();
            return Optional.of(new SchemaDiagnostic.Builder()
                .setRange(target.getRange())
                .setMessage("The argument must be of the form " + displayString() + ".")
                .setSeverity(DiagnosticSeverity.Error)
                .build());
        }

        SchemaNode fieldNode = RankNode.resolveTagValueLeaf(valueNode);
        String fieldName = RankNode.findTagValueText(valueNode);
        Optional<Symbol> scope = CSTUtils.findScope(fieldNode);
        fieldNode.setSymbol(SymbolType.FIELD, context.fileURI(), scope.orElse(null), fieldName);
        fieldNode.getSymbol().setStatus(SymbolStatus.UNRESOLVED);
        context.addUnresolvedFieldArgument(new UnresolvedFieldArgument(
            fieldNode, EnumSet.of(FieldType.STRING), EnumSet.of(IndexingType.INDEX)));
        return Optional.empty();
    }

    private static void markTagKey(ParseContext context, RankNode node) {
        for (Node child : node.getSchemaNode()) {
            if (child.isASTInstance(COLON.class)) {
                break;
            }
            if ("field".equals(child.getText())) {
                ArgumentUtils.modifyNodeSymbol(context, RankNode.wrap(child.getSchemaNode()),
                                               SymbolType.DIMENSION, SymbolStatus.BUILTIN_REFERENCE);
                break;
            }
        }
    }
}
