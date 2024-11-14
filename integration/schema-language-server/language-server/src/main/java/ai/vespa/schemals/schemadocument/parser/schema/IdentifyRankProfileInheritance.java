package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.ArrayList;

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
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isASTInstance(identifierWithDashStr.class)) return ret;
        if (node.getParent() == null || !node.getParent().isASTInstance(inheritsRankProfile.class)) return ret;

        if (!node.hasSymbol()) {
            return ret;
        }

        context.addUnresolvedInheritanceNode(node);

        return ret;
	}

    
}
