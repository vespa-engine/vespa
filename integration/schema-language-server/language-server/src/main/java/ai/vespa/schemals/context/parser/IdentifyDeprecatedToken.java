package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyDeprecatedToken extends Identifier {
    
    public IdentifyDeprecatedToken(PrintStream logger) {
        super(logger);
    }

    private static final HashSet<TokenType> depricatedTokens = new HashSet<TokenType>() {{
        add(TokenType.ATTRIBUTE);
        add(TokenType.ENABLE_BIT_VECTORS);
        add(TokenType.SUMMARY_TO);
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (depricatedTokens.contains(node.getType())) {
            ret.add(
                new Diagnostic(node.getRange(), node.getText() + " is deprecated.", DiagnosticSeverity.Warning, "")
            );
        }

        return ret;
    }
}
