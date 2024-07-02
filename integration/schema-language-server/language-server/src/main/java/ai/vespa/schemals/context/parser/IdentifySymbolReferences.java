package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.SchemaDocumentParser;
import ai.vespa.schemals.context.SchemaIndex;
import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifySymbolReferences extends Identifier {

    protected SchemaDocumentParser document;
    protected SchemaIndex schemaIndex;

    public IdentifySymbolReferences(PrintStream logger, SchemaDocumentParser document, SchemaIndex schemaIndex) {
        super(logger);
        this.document = document;
        this.schemaIndex = schemaIndex;
    }

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        SchemaNode parent = node.getParent();
        if (
            node.getType() == Token.TokenType.FIELDS &&
            parent != null &&
            parent.getIdentifierString() == "ai.vespa.schemals.parser.ast.fieldsElm"
        ) {
            for (int i = 2; i < parent.size(); i += 2) {
                SchemaNode child = parent.get(i);

                if (child.getType() == Token.TokenType.COMMA) {
                    ret.add(new Diagnostic(child.getRange(), "Unexcpeted ',', expected an identifier."));
                    break;
                }

                if (child.getText() != "") {
                    if (schemaIndex.findSymbol(document.getFileURI(), Token.TokenType.FIELD, child.getText()) == null) {
                        ret.add(new Diagnostic(child.getRange(), "Cannot find symbol: " + child.getText()));
                    } else {
                        child.setUserDefinedIdentifier();
                    }
                }

                if (i + 1 < parent.size()) {
                    if (parent.get(i + 1).getType() != Token.TokenType.COMMA) {
                        ret.add(new Diagnostic(parent.get(i + 1).getRange(), "Unexpected token, expected ','"));
                        break;
                    }
                }
            }
        }

        return ret;
    }
}
