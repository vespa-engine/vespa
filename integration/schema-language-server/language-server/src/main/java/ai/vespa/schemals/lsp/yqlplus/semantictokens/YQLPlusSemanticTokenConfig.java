package ai.vespa.schemals.lsp.yqlplus.semantictokens;

import java.util.ArrayList;
import java.util.List;

import ai.vespa.schemals.parser.yqlplus.Token.TokenType;

class YQLPlusSemanticTokenConfig {

    // Keyword
    static final List<TokenType> keywordTokens = new ArrayList<TokenType>() {{
        add(TokenType.SELECT);
        add(TokenType.FROM);
        add(TokenType.WHERE);
    }};
}
