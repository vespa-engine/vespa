package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.INHERITS;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * IdentifySchemaInheritance identifies if a schema inherits from another schema, and add it to the context to resolve the inheritance later
 */
public class IdentifySchemaInheritance extends Identifier<SchemaNode> {

	public IdentifySchemaInheritance(ParseContext context) {
		super(context);
	}

	@Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        if (!node.isASTInstance(identifierStr.class)) return;
        if (node.getParent() == null) return;
        if (!node.getParent().isASTInstance(rootSchema.class)) return;
        if (node.getPreviousSibling() == null) return;
        if (!node.getPreviousSibling().isASTInstance(INHERITS.class)) return;

        if (!node.hasSymbol()) {
            return;
        }

        context.setInheritsSchemaNode(node);
        return;
	}
}
