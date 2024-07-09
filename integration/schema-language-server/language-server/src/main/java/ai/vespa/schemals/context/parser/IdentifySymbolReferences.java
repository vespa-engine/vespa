package ai.vespa.schemals.context.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.fieldsElm;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SymbolReferenceNode;

public class IdentifySymbolReferences extends Identifier {

    public IdentifySymbolReferences(ParseContext context) {
		super(context);
	}

	private Diagnostic createNotFoundError(SchemaNode node, TokenType type) {
        return new Diagnostic(node.getRange(), "Cannot find symbol: " + node.getText() + " of type " + type);
    }

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        SchemaNode parent = node.getParent();
        if (
            node.getType() == TokenType.FIELDS &&
            parent != null &&
            parent.isASTInstance(fieldsElm.class)
        ) {
            for (int i = 2; i < parent.size(); i += 2) {
                SchemaNode child = parent.get(i);

                while (child.size() > 0) {
                    child = child.get(0);
                }

                if (child.getType() == TokenType.COMMA) {
                    ret.add(new Diagnostic(child.getRange(), "Unexcpeted ',', expected an identifier."));
                    break;
                }

                if (child.getText() != "") {
                    child.setType(TokenType.IDENTIFIER);

                    if (context.schemaIndex().findSymbol(context.fileURI(), TokenType.FIELD, child.getText()) == null) {
                        ret.add(createNotFoundError(child, TokenType.FIELD));
                    } else {
                        new SymbolReferenceNode(child);
                    }
                }

                if (i + 1 < parent.size()) {
                    if (parent.get(i + 1).getType() != TokenType.COMMA) {
                        ret.add(new Diagnostic(parent.get(i + 1).getRange(), "Unexpected token, expected ','"));
                        break;
                    }
                }
            }
        }

        return ret;
    }
}
