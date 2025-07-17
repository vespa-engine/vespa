package ai.vespa.schemals.schemadocument.parser.schema;

import java.util.List;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.schemadocument.parser.Identifier;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

/**
 * SwhitchDeprecatedTokenTypes replaces deprecated token with the new tokens to make internal logic easier
 */
public class SwitchDeprecatedTokenTypes extends Identifier<SchemaNode> {

    public SwitchDeprecatedTokenTypes(ParseContext context) {
		super(context);
	}

	private static HashMap<TokenType, TokenType> switchTokenTypeMap = new HashMap<TokenType, TokenType>() {{
        put(TokenType.SEARCH, TokenType.SCHEMA);
        put(TokenType.MACRO, TokenType.FUNCTION);
    }};

    @Override
    public void identify(SchemaNode node, List<Diagnostic> diagnostics) {
        TokenType newType = switchTokenTypeMap.get(node.getSchemaType());
        if (newType != null) {
            node.setSchemaType(newType);
        } 
    }
}
