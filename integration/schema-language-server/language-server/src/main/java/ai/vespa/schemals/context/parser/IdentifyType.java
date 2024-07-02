package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Range;

import com.yahoo.schema.parser.ParsedType;
import com.yahoo.schema.parser.ParsedType.Variant;
import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyType extends Identifier {


    public IdentifyType(PrintStream logger) {
        super(logger);
    }

    private ArrayList<Diagnostic> validateType(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        ParsedType type = ParsedType.fromName(node.getText());
        if (type.getVariant() == Variant.UNKNOWN) {
            ret.add(new Diagnostic(node.getRange(), "Invalid type"));
        } else {
            node.setSchemaType();
        }

        return ret;
    }

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        SchemaNode parent = node.getParent();
        if (
            node.getType() == Token.TokenType.TYPE &&
            parent != null &&
            parent.size() > parent.indexOf(node)
        ) {
            SchemaNode child = parent.get(parent.indexOf(node) + 1);

            // Check if it uses deprecated array
            if (
                child.getClassLeafIdentifierString().equals("dataType") &&
                child.size() > 1 &&
                child.get(1).getText().equals("[]")
            ) {
                Range range = child.getRange();

                child = child.get(0);

                ret.add(new Diagnostic(range, "Data type syntax '" + child.getText() + "[]' is deprecated, use 'array<" + child.getText() + ">' instead.", DiagnosticSeverity.Warning,""));
            }

            // Check if type is valid
            if (
                child.size() > 2 &&
                child.get(1).getType() == Token.TokenType.LESSTHAN
            ) {
                Token.TokenType firstChildType = child.get(0).getType();
                if (
                    firstChildType != Token.TokenType.ANNOTATIONREFERENCE &&
                    firstChildType != Token.TokenType.REFERENCE
                ) {
                    for (int i = 2; i < child.size(); i += 2) {
                        ret.addAll(validateType(child.get(i)));
                    }
                }
            } else if (child.getType() != Token.TokenType.TENSOR_TYPE) {
                ret.addAll(validateType(child));
                child.setType(null);
            }

        }

        return ret;
    }
}
