package ai.vespa.schemals.lsp.yqlplus.semantictokens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.lsp4j.SemanticTokenTypes;

import ai.vespa.schemals.parser.yqlplus.ast.FALSE;
import ai.vespa.schemals.parser.yqlplus.ast.TRUE;
import ai.vespa.schemals.parser.yqlplus.ast.AT;
import ai.vespa.schemals.parser.yqlplus.ast.CONTAINS;
import ai.vespa.schemals.parser.yqlplus.ast.DESC;
import ai.vespa.schemals.parser.yqlplus.ast.FLOAT;
import ai.vespa.schemals.parser.yqlplus.ast.FROM;
import ai.vespa.schemals.parser.yqlplus.ast.INT;
import ai.vespa.schemals.parser.yqlplus.ast.LIMIT;
import ai.vespa.schemals.parser.yqlplus.ast.LONG_INT;
import ai.vespa.schemals.parser.yqlplus.ast.SELECT;
import ai.vespa.schemals.parser.yqlplus.ast.SOURCES;
import ai.vespa.schemals.parser.yqlplus.ast.STRING;
import ai.vespa.schemals.parser.yqlplus.ast.WHERE;
import ai.vespa.schemals.parser.yqlplus.ast.additive_op;
import ai.vespa.schemals.parser.yqlplus.ast.identifierStr;
import ai.vespa.schemals.parser.yqlplus.ast.mult_op;
import ai.vespa.schemals.parser.yqlplus.ast.relational_op;
import ai.vespa.schemals.parser.yqlplus.ast.unary_op;
import ai.vespa.schemals.parser.yqlplus.ast.OR;
import ai.vespa.schemals.parser.yqlplus.ast.ORDERBY;
import ai.vespa.schemals.parser.yqlplus.ast.AND;
import ai.vespa.schemals.parser.yqlplus.ast.ASC;
import ai.vespa.schemals.parser.yqlplus.ast.NOT_IN;
import ai.vespa.schemals.parser.yqlplus.ast.IN;
import ai.vespa.schemals.parser.yqlplus.ast.LIKE;
import ai.vespa.schemals.parser.yqlplus.ast.NOTLIKE;
import ai.vespa.schemals.parser.yqlplus.ast.MATCHES;
import ai.vespa.schemals.parser.yqlplus.ast.NOTMATCHES;

class YQLPlusSemanticTokenConfig {

    // Keyword
    static final List<Class<?>> keywordTokens = new ArrayList<Class<?>>() {{
        add(SELECT.class);
        add(FROM.class);
        add(WHERE.class);
        add(SOURCES.class);
        add(LIMIT.class);
        add(CONTAINS.class);
        add(LIKE.class);
        add(NOTLIKE.class);
        add(MATCHES.class);
        add(NOTMATCHES.class);
        add(ORDERBY.class);
        add(ASC.class);
        add(DESC.class);
    }};

    static final Map<Class<?>, String> tokensMap = new HashMap<Class<?>, String>() {{
        put(mult_op.class, SemanticTokenTypes.Operator);
        put(additive_op.class, SemanticTokenTypes.Operator);
        put(unary_op.class, SemanticTokenTypes.Operator);
        put(relational_op.class, SemanticTokenTypes.Operator);
        put(AT.class, SemanticTokenTypes.Macro);
        put(identifierStr.class, SemanticTokenTypes.Variable);
        put(TRUE.class, SemanticTokenTypes.Type);
        put(FALSE.class, SemanticTokenTypes.Type);
        put(STRING.class, SemanticTokenTypes.String);
        put(LONG_INT.class, SemanticTokenTypes.Number);
        put(INT.class, SemanticTokenTypes.Number);
        put(FLOAT.class, SemanticTokenTypes.Number);
        put(AND.class, SemanticTokenTypes.Operator);
        put(OR.class, SemanticTokenTypes.Operator);
        put(NOT_IN.class, SemanticTokenTypes.Operator);
        put(IN.class, SemanticTokenTypes.Operator);
    }};
}
