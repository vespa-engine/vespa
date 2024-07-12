package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.rootSchemaItem;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyDocumentlessSchema extends Identifier {

	public IdentifyDocumentlessSchema(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();
        if (!node.isASTInstance(rootSchema.class))return ret;

        if (node.size() < 2 || node.get(1).getIsDirty()) {
            // Schema has bad syntax. Missing mandatory document would not be helpful
            return ret;
        }

        // Look for document definition inside
        for (SchemaNode child : node) {
            if (!child.isASTInstance(rootSchemaItem.class)) continue;
            if (child.size() == 0) continue;
            if (child.get(0).isASTInstance(documentElm.class)) return ret; // Found document
        }

        // TODO: quickfix
        ret.add(new Diagnostic(node.get(0).getRange(), "Missing mandatory document definition in schema body."));
        return ret;
	}
}
