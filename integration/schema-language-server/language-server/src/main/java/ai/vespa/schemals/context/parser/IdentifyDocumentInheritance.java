package ai.vespa.schemals.context.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.inheritsDocument;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyDocumentInheritance extends Identifier {

	public IdentifyDocumentInheritance(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();
        if (!node.isASTInstance(inheritsDocument.class))return ret;

        for (int i = 0; i < node.size(); i++) {
            SchemaNode child = node.get(i);

            if (!child.isASTInstance(identifierStr.class)) continue;

            this.context.addUnresolvedInheritanceNode(child);
        }

        return ret;
	}
}
