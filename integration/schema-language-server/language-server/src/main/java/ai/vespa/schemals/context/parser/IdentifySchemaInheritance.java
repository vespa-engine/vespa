package ai.vespa.schemals.context.parser;

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
        if (!node.isASTInstance(rootSchema.class)) return ret;

        for (int i = 0; i < node.size() - 1; ++i) {
            if (node.get(i).getType() == TokenType.INHERITS && node.get(i+1).isASTInstance(identifierStr.class)) {
                context.setInheritsSchemaNode(node.get(i+1));
            }
        }

        return ret;
	}
}
