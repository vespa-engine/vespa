package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
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
                new SchemaDiagnostic.Builder()
                    .setRange(node.getRange())
                    .setMessage(node.getText() + " is deprecated. " + message)
                    .setSeverity(DiagnosticSeverity.Warning)
                    .build()
            );
        }

        return ret;
    }
}
