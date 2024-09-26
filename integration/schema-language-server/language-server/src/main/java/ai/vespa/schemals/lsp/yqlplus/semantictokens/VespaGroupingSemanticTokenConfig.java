package ai.vespa.schemals.lsp.yqlplus.semantictokens;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.lsp4j.SemanticTokenTypes;

import ai.vespa.schemals.parser.grouping.ast.*;

class VespaGroupingSemanticToken {

    private static final String timeFunctionType = SemanticTokenTypes.Method;
    private static final String mathFunctionType = SemanticTokenTypes.Method;
    private static final String operationBodyFunction = SemanticTokenTypes.Function;
    private static final String simpleExpressions = SemanticTokenTypes.Macro;

    static final Map<Class<?>, String> tokensMap = new HashMap<Class<?>, String>() {{
        put(identifierStr.class, SemanticTokenTypes.Variable);
        put(INFIX_ADD.class, SemanticTokenTypes.Operator);
        put(INFIX_DIV.class, SemanticTokenTypes.Operator);
        put(INFIX_MOD.class, SemanticTokenTypes.Operator);
        put(INFIX_MUL.class, SemanticTokenTypes.Operator);
        put(INFIX_SUB.class, SemanticTokenTypes.Operator);
        put(EQ.class, SemanticTokenTypes.Operator);
        put(LT.class, SemanticTokenTypes.Operator);
        put(GT.class, SemanticTokenTypes.Operator);
        put(INF.class, SemanticTokenTypes.Number);
        put(NEGINF.class, SemanticTokenTypes.Number);
        put(number.class, SemanticTokenTypes.Number);
        put(stringElm.class, SemanticTokenTypes.String);
        put(STRING.class, SemanticTokenTypes.String);
        put(TRUE.class, SemanticTokenTypes.Type);
        put(FALSE.class, SemanticTokenTypes.Type);
        
        put(TIME.class, SemanticTokenTypes.Class);
        put(TIME_DATE.class, timeFunctionType);
        put(TIME_DAYOFMONTH.class, timeFunctionType);
        put(TIME_DAYOFWEEK.class, timeFunctionType);
        put(TIME_DAYOFYEAR.class, timeFunctionType);
        put(TIME_HOUROFDAY.class, timeFunctionType);
        put(TIME_MINUTEOFHOUR.class, timeFunctionType);
        put(TIME_MONTHOFYEAR.class, timeFunctionType);
        put(TIME_SECONDOFMINUTE.class, timeFunctionType);
        put(TIME_YEAR.class, timeFunctionType);

        put(MATH.class, SemanticTokenTypes.Class);
        put(POW.class, mathFunctionType);
        put(HYPOT.class, mathFunctionType);
        put(EXP.class, mathFunctionType);
        put(LOG.class, mathFunctionType);
        put(LOG1P.class, mathFunctionType);
        put(LOG10.class, mathFunctionType);
        put(SIN.class, mathFunctionType);
        put(ASIN.class, mathFunctionType);
        put(COS.class, mathFunctionType);
        put(ACOS.class, mathFunctionType);
        put(TAN.class, mathFunctionType);
        put(ATAN.class, mathFunctionType);
        put(SQRT.class, mathFunctionType);
        put(SINH.class, mathFunctionType);
        put(ASINH.class, mathFunctionType);
        put(COSH.class, mathFunctionType);
        put(ACOSH.class, mathFunctionType);
        put(TANH.class, mathFunctionType);
        put(ATANH.class, mathFunctionType);
        put(FLOOR.class, mathFunctionType);
        put(CBRT.class, mathFunctionType);

        put(ALL.class, SemanticTokenTypes.Keyword);
        put(EACH.class, SemanticTokenTypes.Keyword);

        put(GROUP.class, operationBodyFunction);
        put(ACCURACY.class, operationBodyFunction);
        put(ALIAS.class, operationBodyFunction);
        put(HINT.class, operationBodyFunction);
        put(MAX.class, operationBodyFunction);
        put(ORDER.class, operationBodyFunction);
        put(OUTPUT.class, operationBodyFunction);
        put(PRECISION.class, operationBodyFunction);
        put(WHERE.class, operationBodyFunction);

        put(AS.class, SemanticTokenTypes.Keyword);
        put(AT.class, SemanticTokenTypes.Keyword);

        put(ADD.class, simpleExpressions);
        put(ALIAS.class, simpleExpressions); // TODO: Verify that this is a function
        put(ATTRIBUTE.class, simpleExpressions);
        put(AVG.class, simpleExpressions);
        put(BUCKET.class, simpleExpressions);
        put(CAT.class, simpleExpressions);
        put(COUNT.class, simpleExpressions);
        put(DEBUGWAIT.class, simpleExpressions); // TODO: Maybe add a warning on this function
        put(DOCIDNSSPECIFIC.class, simpleExpressions);
        put(FIXEDWIDTH.class, simpleExpressions);
        put(MAX.class, simpleExpressions);
        put(MD5.class, simpleExpressions);
        put(MIN.class, simpleExpressions);
        put(MOD.class, simpleExpressions);
        put(MUL.class, simpleExpressions);
        put(NEG.class, simpleExpressions);
        put(NORMALIZESUBJECT.class, simpleExpressions);
        put(NOW.class, simpleExpressions);
        put(OR.class, simpleExpressions);
        put(PREDEFINED.class, simpleExpressions);
        put(RELEVANCE.class, simpleExpressions);
        put(REVERSE.class, simpleExpressions);
        put(SIZE.class, simpleExpressions);
        put(SORT.class, simpleExpressions);
        put(INTERPOLATEDLOOKUP.class, simpleExpressions);
        put(STDDEV.class, simpleExpressions);
        put(STRCAT.class, simpleExpressions);
        put(STRLEN.class, simpleExpressions);
        put(SUB.class, simpleExpressions);
        put(SUM.class, simpleExpressions);
        put(SUMMARY.class, simpleExpressions);
        put(TODOUBLE.class, simpleExpressions);
        put(TOLONG.class, simpleExpressions);
        put(TORAW.class, simpleExpressions);
        put(TOSTRING.class, simpleExpressions);
        put(UCA.class, simpleExpressions);
        put(XOR.class, simpleExpressions);
        put(XORBIT.class, simpleExpressions);

        put(ZCURVE.class, SemanticTokenTypes.Class);
        put(X.class, SemanticTokenTypes.Method);
        put(Y.class, SemanticTokenTypes.Method);
    }};
}
