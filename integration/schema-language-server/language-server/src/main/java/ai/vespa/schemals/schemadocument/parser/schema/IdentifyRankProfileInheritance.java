package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.inheritsRankProfile;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * IdentifyRankProfileInheritance indentifies inheritance of a rank-profile and adds it to a list to resolve later.
 */
public class IdentifyRankProfileInheritance extends Identifier<SchemaNode> {

	public IdentifyRankProfileInheritance(ParseContext context) {
		super(context);
	}

	@Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.isASTInstance(identifierWithDashStr.class)) return;
        if (node.getParent() == null || !node.getParent().isASTInstance(inheritsRankProfile.class)) return;

        if (!node.hasSymbol()) {
            return;
        }

        context.addUnresolvedInheritanceNode(node);
	}
}
