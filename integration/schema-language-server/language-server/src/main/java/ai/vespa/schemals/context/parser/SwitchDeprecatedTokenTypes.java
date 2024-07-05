package ai.vespa.schemals.context.parser;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.tree.SchemaNode;

public class SwitchDeprecatedTokenTypes extends Identifier {


    public SwitchDeprecatedTokenTypes(PrintStream logger) {
        super(logger);
    }

    private static HashMap<TokenType, TokenType> swicthTokenTypeMap = new HashMap<TokenType, TokenType>() {{
        put(TokenType.SEARCH, TokenType.SCHEMA);
        put(TokenType.MACRO, TokenType.FUNCTION);
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        TokenType newType = swicthTokenTypeMap.get(node.getType());
        if (newType != null) {
            node.setType(newType);
        } 

        return ret;
    }
}
