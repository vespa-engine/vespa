package ai.vespa.schemals.context.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SymbolReferenceNode;

public class IdentifySchemaInheritance extends Identifier {

	public IdentifySchemaInheritance(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isASTInstance(identifierStr.class)) return ret;
        if (node.getParent() == null) return ret;
        if (!node.getParent().isASTInstance(rootSchema.class)) return ret;
        if (node.getPreviousSibling() == null) return ret;
        if (node.getPreviousSibling().getType() != TokenType.INHERITS) return ret;

        if (!(node instanceof SymbolReferenceNode)) {
            ret.add(new Diagnostic(node.getRange(), "Should be symbol reference", DiagnosticSeverity.Warning, ""));
        }

        context.setInheritsSchemaNode(node);

        return ret;
	}
}
