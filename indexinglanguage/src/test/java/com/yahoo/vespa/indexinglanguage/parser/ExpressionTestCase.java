// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.expressions.*;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings({ "rawtypes" })
public class ExpressionTestCase {

    @Test
    public void requireThatAllExpressionTypesAreParsed() throws ParseException {
        assertExpression(ArithmeticExpression.class, "1 + 2");
        assertExpression(AttributeExpression.class, "attribute");
        assertExpression(Base64DecodeExpression.class, "base64decode");
        assertExpression(Base64EncodeExpression.class, "base64encode");
        assertExpression(CatExpression.class, "1 . 2");
        assertExpression(ClearStateExpression.class, "clear_state");
        assertExpression(EchoExpression.class, "echo");
        assertExpression(ExactExpression.class, "exact");
        assertExpression(FlattenExpression.class, "flatten");
        assertExpression(ForEachExpression.class, "for_each { 1 }");
        assertExpression(GetFieldExpression.class, "get_field field1");
        assertExpression(GetVarExpression.class, "get_var myvar1");
        assertExpression(GuardExpression.class, "guard { 1 }");
        assertExpression(GuardExpression.class, "guard { 1; 2 }");
        assertExpression(HexDecodeExpression.class, "hexdecode");
        assertExpression(HexEncodeExpression.class, "hexencode");
        assertExpression(HostNameExpression.class, "hostname");
        assertExpression(IfThenExpression.class, "if (1 < 2) { 3 }");
        assertExpression(IfThenExpression.class, "if (1 < 2) { 3 } else { 4 }");
        assertExpression(IndexExpression.class, "index");
        assertExpression(InputExpression.class, "input foo");
        assertExpression(InputExpression.class, "input field1");
        assertExpression(JoinExpression.class, "join '1'");
        assertExpression(LowerCaseExpression.class, "lowercase");
        assertExpression(NGramExpression.class, "ngram 1");
        assertExpression(NormalizeExpression.class, "normalize");
        assertExpression(NowExpression.class, "now");
        assertExpression(OptimizePredicateExpression.class, "optimize_predicate");
        assertExpression(ParenthesisExpression.class, "(1)");
        assertExpression(RandomExpression.class, "random");
        assertExpression(RandomExpression.class, "random 1");
        assertExpression(ScriptExpression.class, "{ 1; 2 }");
        assertExpression(SelectInputExpression.class, "select_input { field1: 2; }");
        assertExpression(SetLanguageExpression.class, "set_language");
        assertExpression(SetValueExpression.class, "1");
        assertExpression(SetVarExpression.class, "set_var myvar1");
        assertExpression(SplitExpression.class, "split '1'");
        assertExpression(StatementExpression.class, "1 | 2");
        assertExpression(SubstringExpression.class, "substring 1 2");
        assertExpression(SummaryExpression.class, "summary");
        assertExpression(SwitchExpression.class, "switch { case '1': 2; }");
        assertExpression(ThisExpression.class, "this");
        assertExpression(ToArrayExpression.class, "to_array");
        assertExpression(ToByteExpression.class, "to_byte");
        assertExpression(ToDoubleExpression.class, "to_double");
        assertExpression(ToFloatExpression.class, "to_float");
        assertExpression(ToIntegerExpression.class, "to_int");
        assertExpression(TokenizeExpression.class, "tokenize");
        assertExpression(TokenizeExpression.class, "tokenize stem");
        assertExpression(TokenizeExpression.class, "tokenize stem normalize");
        assertExpression(TokenizeExpression.class, "tokenize stem:\"ALL\" normalize");
        assertExpression(TokenizeExpression.class, "tokenize stem:\"ALL\"");
        assertExpression(TokenizeExpression.class, "tokenize normalize");
        assertExpression(ToLongExpression.class, "to_long");
        assertExpression(ToPositionExpression.class, "to_pos");
        assertExpression(ToStringExpression.class, "to_string");
        assertExpression(ToWsetExpression.class, "to_wset");
        assertExpression(ToBoolExpression.class, "to_bool");
        assertExpression(ToWsetExpression.class, "to_wset create_if_non_existent");
        assertExpression(ToWsetExpression.class, "to_wset remove_if_zero");
        assertExpression(ToWsetExpression.class, "to_wset create_if_non_existent remove_if_zero");
        assertExpression(ToWsetExpression.class, "to_wset remove_if_zero create_if_non_existent");
        assertExpression(TrimExpression.class, "trim");
        assertExpression(ZCurveExpression.class, "zcurve");
        assertExpression(ChoiceExpression.class, "input foo || \"\"");
    }

    private static void assertExpression(Class expectedClass, String str) throws ParseException {
        Linguistics linguistics = new SimpleLinguistics();
        Expression foo = Expression.fromString(str, linguistics, Embedder.throwsOnUse.asMap());
        assertEquals(expectedClass, foo.getClass());
        Expression bar = Expression.fromString(foo.toString(), linguistics, Embedder.throwsOnUse.asMap());
        assertEquals(foo.hashCode(), bar.hashCode());
        assertEquals(foo, bar);
    }

}
