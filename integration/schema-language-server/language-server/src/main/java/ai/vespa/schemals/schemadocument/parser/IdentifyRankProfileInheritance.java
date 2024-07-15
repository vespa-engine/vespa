package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.inheritsRankProfile;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyRankProfileInheritance extends Identifier {

	public IdentifyRankProfileInheritance(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isASTInstance(identifierWithDashStr.class)) return ret;
        if (node.getParent() == null || !node.getParent().isASTInstance(inheritsRankProfile.class)) return ret;

        if (!node.hasSymbol()) {
            ret.add(new Diagnostic(node.getRange(), "Should be reference", DiagnosticSeverity.Warning, ""));
        }

        context.addUnresolvedInheritanceNode(node);

        return ret;
	}

    
}
