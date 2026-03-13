// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.simple.SimpleLinguistics;
import com.yahoo.vespa.indexinglanguage.expressions.ArithmeticExpression;
import com.yahoo.vespa.indexinglanguage.expressions.AttributeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Base64DecodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Base64EncodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.CatExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ChoiceExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ClearStateExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ConstantExpression;
import com.yahoo.vespa.indexinglanguage.expressions.EchoExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ExactExpression;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.FlattenExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ForEachExpression;
import com.yahoo.vespa.indexinglanguage.expressions.GetFieldExpression;
import com.yahoo.vespa.indexinglanguage.expressions.GetVarExpression;
import com.yahoo.vespa.indexinglanguage.expressions.GuardExpression;
import com.yahoo.vespa.indexinglanguage.expressions.HexDecodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.HexEncodeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.HostNameExpression;
import com.yahoo.vespa.indexinglanguage.expressions.IfThenExpression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.JoinExpression;
import com.yahoo.vespa.indexinglanguage.expressions.LowerCaseExpression;
import com.yahoo.vespa.indexinglanguage.expressions.NGramExpression;
import com.yahoo.vespa.indexinglanguage.expressions.NormalizeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.NowExpression;
import com.yahoo.vespa.indexinglanguage.expressions.OptimizePredicateExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ParenthesisExpression;
import com.yahoo.vespa.indexinglanguage.expressions.RandomExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ScriptExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SelectInputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SetLanguageExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SetVarExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SplitExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SubstringExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SummaryExpression;
import com.yahoo.vespa.indexinglanguage.expressions.SwitchExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ThisExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToArrayExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToBoolExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToByteExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToDoubleExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToFloatExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToIntegerExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToLongExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToPositionExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToStringExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ToWsetExpression;
import com.yahoo.vespa.indexinglanguage.expressions.TokenizeExpression;
import com.yahoo.vespa.indexinglanguage.expressions.TrimExpression;
import com.yahoo.vespa.indexinglanguage.expressions.ZCurveExpression;
import org.junit.Test;

import java.util.Map;
import java.util.Optional;

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
        assertExpression(ExactExpression.class, "exact max-token-length: 10", Optional.of("exact max-token-length:10"));
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
        assertExpression(ConstantExpression.class, "1");
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
        assertExpression(TokenizeExpression.class, "tokenize max-occurrences: 15", Optional.of("tokenize max-occurrences:15"));
        assertExpression(TokenizeExpression.class, "tokenize max-token-length: 15", Optional.of("tokenize max-token-length:15"));
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
        assertExpression(expectedClass, str, Optional.empty());
    }

    private static void assertExpression(Class expectedClass, String str, Optional<String> expStr) throws ParseException {
        Linguistics linguistics = new SimpleLinguistics();
        Expression foo = Expression.fromString(str, linguistics, Map.of(), Embedder.throwsOnUse.asMap(), Map.of());
        assertEquals(expectedClass, foo.getClass());
        if (expStr.isPresent()) {
            assertEquals(expStr.get(), foo.toString());
        }
        Expression bar = Expression.fromString(foo.toString(), linguistics, Map.of(), Embedder.throwsOnUse.asMap(), Map.of());
        assertEquals(foo.hashCode(), bar.hashCode());
        assertEquals(foo, bar);
    }

}
