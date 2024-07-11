package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public class SwitchDeprecatedTokenTypes extends Identifier {

    public SwitchDeprecatedTokenTypes(ParseContext context) {
		super(context);
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
