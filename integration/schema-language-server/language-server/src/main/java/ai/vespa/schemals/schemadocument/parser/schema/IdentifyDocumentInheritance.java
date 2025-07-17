package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * Identify document inherance and add it to a list to resolve later
 */
public class IdentifyDocumentInheritance extends Identifier<SchemaNode> {

	public IdentifyDocumentInheritance(ParseContext context) {
		super(context);
	}

	@Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.isASTInstance(identifierStr.class)) return;
        if (node.getParent() == null || !node.getParent().isASTInstance(inheritsDocument.class)) return;

        if (!node.hasSymbol()) {
            return;
        }

        this.context.addUnresolvedInheritanceNode(node);
        return;
	}
}
