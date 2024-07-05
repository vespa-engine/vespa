package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.context.SchemaIndex;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.fieldsElm;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.SymbolNode;
import ai.vespa.schemals.tree.SymbolReferenceNode;

public class IdentifySymbolReferences extends Identifier {

    protected SchemaDocumentParser document;
    protected SchemaIndex schemaIndex;

    public IdentifySymbolReferences(PrintStream logger, SchemaDocumentParser document, SchemaIndex schemaIndex) {
        super(logger);
        this.document = document;
        this.schemaIndex = schemaIndex;
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
            parent.instanceOf(fieldsElm.class)
        ) {
            for (int i = 2; i < parent.size(); i += 2) {
                SchemaNode child = parent.get(i);

                if (child.getType() == TokenType.COMMA) {
                    ret.add(new Diagnostic(child.getRange(), "Unexcpeted ',', expected an identifier."));
                    break;
                }

                if (child.getText() != "") {
                    child.setType(TokenType.IDENTIFIER);

                    if (schemaIndex.findSymbol(document.getFileURI(), TokenType.FIELD, child.getText()) == null) {
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

        if (node.getType() == TokenType.INHERITS) {
            SchemaNode nextNode = node.getNext();
            SchemaNode previousNode = node.getPrevious();
            if (previousNode != null && !previousNode.isLeaf()) {
                previousNode = previousNode.getPrevious();
            }

            SchemaNode typeSpecifierNode = (previousNode == null) ? null : previousNode.getPrevious();

            if (
                previousNode != null &&
                previousNode instanceof SymbolNode &&
                nextNode != null &&
                typeSpecifierNode != null
            ) {
                nextNode.setType(TokenType.IDENTIFIER);

                if (schemaIndex.findSymbol(document.getFileURI(), typeSpecifierNode.getType(), nextNode.getText()) == null) {
                    ret.add(createNotFoundError(nextNode, typeSpecifierNode.getType()));
                } else {
                    new SymbolReferenceNode(nextNode);
                }
            }
        }

        return ret;
    }
}
