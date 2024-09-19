package ai.vespa.schemals.lsp.yqlplus.semantictokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.SemanticTokenTypes;

import ai.vespa.schemals.parser.ast.FALSE;
import ai.vespa.schemals.parser.ast.TRUE;
import ai.vespa.schemals.parser.yqlplus.ast.FROM;
import ai.vespa.schemals.parser.yqlplus.ast.LIMIT;
import ai.vespa.schemals.parser.yqlplus.ast.SELECT;
import ai.vespa.schemals.parser.yqlplus.ast.SOURCES;
import ai.vespa.schemals.parser.yqlplus.ast.WHERE;
import ai.vespa.schemals.parser.yqlplus.ast.additive_op;
import ai.vespa.schemals.parser.yqlplus.ast.identifierStr;
import ai.vespa.schemals.parser.yqlplus.ast.mult_op;
import ai.vespa.schemals.parser.yqlplus.ast.relational_op;
import ai.vespa.schemals.parser.yqlplus.ast.unary_op;

class YQLPlusSemanticTokenConfig {

    // Keyword
    static final List<Class<?>> keywordTokens = new ArrayList<Class<?>>() {{
        add(SELECT.class);
        add(FROM.class);
        add(WHERE.class);
        add(SOURCES.class);
        add(LIMIT.class);
    }};

    static final Map<Class<?>, String> tokensMap = new HashMap<Class<?>, String>() {{
        put(mult_op.class, SemanticTokenTypes.Operator);
        put(additive_op.class, SemanticTokenTypes.Operator);
        put(unary_op.class, SemanticTokenTypes.Operator);
        put(relational_op.class, SemanticTokenTypes.Operator);
        put(identifierStr.class, SemanticTokenTypes.Variable);
        put(TRUE.class, SemanticTokenTypes.Type);
        put(FALSE.class, SemanticTokenTypes.Type);
    }};
}
