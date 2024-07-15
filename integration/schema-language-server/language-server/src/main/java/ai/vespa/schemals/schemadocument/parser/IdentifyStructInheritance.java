package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.inheritsStruct;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyStructInheritance extends Identifier {

	public IdentifyStructInheritance(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isSchemaASTInstance(identifierStr.class)) return ret;
        if (node.getParent() == null || !node.getParent().isSchemaASTInstance(inheritsStruct.class)) return ret;

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
