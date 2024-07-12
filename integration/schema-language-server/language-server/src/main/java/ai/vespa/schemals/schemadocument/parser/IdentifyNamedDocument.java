package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

/*
 * Should run after symbol definition identifiers
 */
public class IdentifyNamedDocument extends Identifier {

	public IdentifyNamedDocument(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();
        if (!node.isASTInstance(documentElm.class))return ret;

        if (node.size() < 2 || !node.get(1).isASTInstance(identifierStr.class))return ret;

        Range identifierRange = node.get(1).getRange();
        String documentName = node.get(1).getText();

        if (context.schemaIndex().findSymbolInFile(context.fileURI(), SymbolType.SCHEMA, documentName) == null) {
            // TODO: Quickfix
            ret.add(new Diagnostic(identifierRange, "Invalid document name \"" + documentName + "\". The document name must match the containing schema's name."));
        }

        return ret;
	}
}
