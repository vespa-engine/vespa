package ai.vespa.schemals.lsp.yqlplus.semantictokens;

import java.util.ArrayList;
import java.util.List;

import ai.vespa.schemals.parser.yqlplus.ast.FROM;
import ai.vespa.schemals.parser.yqlplus.ast.SELECT;
import ai.vespa.schemals.parser.yqlplus.ast.WHERE;

class YQLPlusSemanticTokenConfig {

    // Keyword
    static final List<Class<?>> keywordTokens = new ArrayList<Class<?>>() {{
        add(SELECT.class);
        add(FROM.class);
        add(WHERE.class);
    }};
}
