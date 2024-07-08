package ai.vespa.schemals.context.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import com.yahoo.schema.parser.ParsedType;
import com.yahoo.schema.parser.ParsedType.Variant;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.ast.dataType;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.TypeNode;

public class IdentifyType extends Identifier {
    public IdentifyType(ParseContext context) {
		super(context);
	}

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        if (!node.isASTInstance(dataType.class)) {
            return ret;
        }

        TypeNode replacedNode = new TypeNode(node); // Will maintain tree

        ParsedType parsedType = replacedNode.getParsedType();

        if (replacedNode.isArrayOldStyle()) {
            String nodeText = replacedNode.get(0).getText();
            ret.add(new Diagnostic(
                replacedNode.getRange(), 
                "Data type syntax '" + nodeText + "[]' is deprecated, use 'array<" + nodeText + ">' instead.", 
                DiagnosticSeverity.Warning,""
            ));

        }

        if (parsedType.getVariant() != Variant.UNKNOWN) {
            return ret;
        }

        this.context.addUnresolvedTypeNode(replacedNode);
        return ret;
    }
}
