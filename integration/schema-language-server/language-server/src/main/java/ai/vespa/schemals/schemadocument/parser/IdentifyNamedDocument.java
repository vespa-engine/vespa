package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.context.ParseContext;
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
        if (!node.isSchemaASTInstance(documentElm.class))return ret;

        if (node.size() < 2 || !node.get(1).isSchemaASTInstance(identifierStr.class))return ret;

        Range identifierRange = node.get(1).getRange();
        String documentName = node.get(1).getText();
        Optional<Symbol> schemaSymbol = context.schemaIndex().getSymbol(SymbolType.SCHEMA, documentName);
        if (schemaSymbol.isEmpty() || schemaSymbol.get().getFileURI() != context.fileURI()) {
            // TODO: Quickfix
            ret.add(new Diagnostic(identifierRange, "Invalid document name \"" + documentName + "\". The document name must match the containing schema's name."));
        }

        return ret;
	}
}
