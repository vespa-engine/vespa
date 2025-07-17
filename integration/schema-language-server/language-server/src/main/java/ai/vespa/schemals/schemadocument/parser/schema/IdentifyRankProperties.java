package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.SubLanguageData;
import ai.vespa.schemals.parser.schemaRankPropertyKey;
import ai.vespa.schemals.parser.ast.IDENTIFIER;
import ai.vespa.schemals.parser.ast.IDENTIFIER_WITH_DASH;
import ai.vespa.schemals.parser.ast.rankProperty;
import ai.vespa.schemals.parser.ast.rankPropertyItem;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.Node;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * The purpose of this identifier is to identify each element inside a rank-properties { } block.
 * Rank property keys seem to never be actually parsed, but rather reconstructed from an instantiated feature.
 * So the SchemaParser really only treats the whole property key as a string, and this string gets propagated down to searchlib.
 *
 * This means that we need to rewrite the CST if we want LSP features on rank-properties.
 */
public class IdentifyRankProperties extends Identifier<SchemaNode> {

    public IdentifyRankProperties(ParseContext context) {
        super(context);
    }

    @Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.isASTInstance(rankProperty.class)) return;

        if ( node.size() != 3
         || !node.get(0).isASTInstance(rankPropertyItem.class) ) return; // malformed schema

        Node leaf = node.findFirstLeaf();
        if (!leaf.isASTInstance(IDENTIFIER.class) && 
            !leaf.isASTInstance(IDENTIFIER_WITH_DASH.class)) {
            return;
        }

        // Replace the rankPropertyItem corresponding to the rank-property key
        // with a new node marked as containing ranking expression data.
        // This will make it get handled by the ranking expression parser later.
        String keyData = node.get(0).getText();
        rankPropertyItem oldASTNode = (rankPropertyItem)(node.get(0).getSchemaNode().getOriginalSchemaNode());

        schemaRankPropertyKey newASTNode = new schemaRankPropertyKey();

        newASTNode.setPropertyName(new SubLanguageData(keyData, 0));
        newASTNode.setTokenSource(oldASTNode.getTokenSource());
        newASTNode.setBeginOffset(oldASTNode.getBeginOffset());
        newASTNode.setEndOffset(oldASTNode.getEndOffset());

        SchemaNode newNode = new SchemaNode(newASTNode);

        node.insertChildAfter(0, newNode);
        node.removeChild(0);

        return;
    }
}
