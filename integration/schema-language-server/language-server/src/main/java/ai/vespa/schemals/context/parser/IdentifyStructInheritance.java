package ai.vespa.schemals.context.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.inheritsStruct;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyStructInheritance extends Identifier {

	public IdentifyStructInheritance(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isASTInstance(identifierStr.class)) return ret;
        if (node.getParent() == null || !node.getParent().isASTInstance(inheritsStruct.class)) return ret;

        if (!node.hasSymbol()) {
            ret.add(new Diagnostic(node.getRange(), "Should be reference", DiagnosticSeverity.Warning, ""));
        }

        ret.add(new Diagnostic(
            node.getRange(),
            "Inherits struct",
            DiagnosticSeverity.Information,
            ""
        ));

        return ret;
	}

    
}
