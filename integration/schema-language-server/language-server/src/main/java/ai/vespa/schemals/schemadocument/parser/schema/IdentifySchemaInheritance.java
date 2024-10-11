package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.Token.TokenType;
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
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isSchemaASTInstance(identifierStr.class)) return ret;
        if (node.getParent() == null) return ret;
        if (!node.getParent().getSchemaNode().isSchemaASTInstance(rootSchema.class)) return ret;
        if (node.getPreviousSibling() == null) return ret;
        if (node.getPreviousSibling().getSchemaNode().getSchemaType() != TokenType.INHERITS) return ret;

        if (!node.hasSymbol()) {
            return ret;
        }

        context.setInheritsSchemaNode(node);

        return ret;
	}
}
