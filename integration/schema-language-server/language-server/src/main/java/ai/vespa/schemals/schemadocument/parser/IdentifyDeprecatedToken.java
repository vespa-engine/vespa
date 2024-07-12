package ai.vespa.schemals.schemadocument.parser;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyDeprecatedToken extends Identifier {
    public IdentifyDeprecatedToken(ParseContext context) {
		super(context);
	}

	private static final HashMap<TokenType, String> deprecatedTokens = new HashMap<TokenType, String>() {{
        put(TokenType.ATTRIBUTE, "");
        put(TokenType.ENABLE_BIT_VECTORS, "");
        put(TokenType.INDEX, "");
        put(TokenType.SUMMARY_TO, "");
        put(TokenType.SEARCH, "Use schema instead.");
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        // TODO: semantic context
        ArrayList<Diagnostic> ret = new ArrayList<>();

        String message = deprecatedTokens.get(node.getSchemaType());
        if (message != null) {
            ret.add(
                new Diagnostic(node.getRange(), node.getText() + " is deprecated. " + message, DiagnosticSeverity.Warning, "")
            );
        }

        return ret;
    }
}
