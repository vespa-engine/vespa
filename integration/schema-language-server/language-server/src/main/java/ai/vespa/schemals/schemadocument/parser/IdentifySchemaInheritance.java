package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifySchemaInheritance extends Identifier {

	public IdentifySchemaInheritance(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isSchemaASTInstance(identifierStr.class)) return ret;
        if (node.getParent() == null) return ret;
        if (!node.getParent().isSchemaASTInstance(rootSchema.class)) return ret;
        if (node.getPreviousSibling() == null) return ret;
        if (node.getPreviousSibling().getSchemaType() != TokenType.INHERITS) return ret;

        if (!node.hasSymbol()) {
            return ret;
        }

        context.setInheritsSchemaNode(node);

        return ret;
	}
}
