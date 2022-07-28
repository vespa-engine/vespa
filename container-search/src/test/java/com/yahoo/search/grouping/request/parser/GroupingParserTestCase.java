// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request.parser;

import com.yahoo.search.grouping.request.AllOperation;
import com.yahoo.search.grouping.request.AttributeMapLookupValue;
import com.yahoo.search.grouping.request.EachOperation;
import com.yahoo.search.grouping.request.GroupingOperation;
import com.yahoo.search.query.parser.Parsable;
import com.yahoo.search.query.parser.ParserEnvironment;
import com.yahoo.search.yql.VespaGroupingStep;
import com.yahoo.search.yql.YqlParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingParserTestCase {

    @Test
    void requireThatMathAllowsWhitespace() {
        for (String op : Arrays.asList("+", " +", " + ", "+ ",
                                       "-", " -", " - ", "- ",
                                       "*", " *", " * ", "* ",
                                       "/", " /", " / ", "/ ",
                                       "%", " %", " % ", "% ")) {
            assertParse("all(group(foo " + op + " 69) each(output(count())))");
            assertParse("all(group(foo " + op + " 6 " + op + " 9) each(output(count())))");
            assertParse("all(group(69 " + op + " foo) each(output(count())))");
            assertParse("all(group(6 " + op + " 9 " + op + " foo) each(output(count())))");
        }
    }

    @Test
    void testRequestList() {
        List<GroupingOperation> lst = GroupingOperation.fromStringAsList("all();each();all() where(true);each()");
        assertNotNull(lst);
        assertEquals(4, lst.size());
        assertTrue(lst.get(0) instanceof AllOperation);
        assertTrue(lst.get(1) instanceof EachOperation);
        assertTrue(lst.get(2) instanceof AllOperation);
        assertTrue(lst.get(3) instanceof EachOperation);
    }

    @Test
    void testAttributeFunctions() {
        assertParse("all(group(foo) each(output(sum(attribute(bar)))))",
                "all(group(foo) each(output(sum(attribute(bar)))))");
        assertParse("all(group(foo) each(output(sum(interpolatedlookup(bar, 0.25)))))",
                "all(group(foo) each(output(sum(interpolatedlookup(bar, 0.25)))))");
        assertParse("all(group(foo) each(output(sum(array.at(bar, 42.0)))))",
                "all(group(foo) each(output(sum(array.at(bar, 42.0)))))");
    }

    @Test
    void requireThatTokenImagesAreNotReservedWords() {
        List<String> images = Arrays.asList("acos",
                "acosh",
                "accuracy",
                "add",
                "alias",
                "all",
                "and",
                "array",
                "as",
                "at",
                "asin",
                "asinh",
                "atan",
                "atanh",
                "attribute",
                "avg",
                "bucket",
                "cat",
                "cbrt",
                "cos",
                "cosh",
                "count",
                "debugwait",
                "div",
                "docidnsspecific",
                "each",
                "exp",
                "fixedwidth",
                "floor",
                "group",
                "hint",
                "hypot",
                "log",
                "log1p",
                "log10",
                "math",
                "max",
                "md5",
                "min",
                "mod",
                "mul",
                "neg",
                "normalizesubject",
                "now",
                "or",
                "order",
                "output",
                "pow",
                "precision",
                "predefined",
                "relevance",
                "reverse",
                "sin",
                "sinh",
                "size",
                "sort",
                "stddev",
                "interpolatedlookup",
                "sqrt",
                "strcat",
                "strlen",
                "sub",
                "sum",
                "summary",
                "tan",
                "tanh",
                "time",
                "date",
                "dayofmonth",
                "dayofweek",
                "dayofyear",
                "hourofday",
                "minuteofhour",
                "monthofyear",
                "secondofminute",
                "year",
                "todouble",
                "tolong",
                "toraw",
                "tostring",
                "true",
                "false",
                "uca",
                "where",
                "x",
                "xor",
                "xorbit",
                "y",
                "zcurve");
        for (String image : images) {
            assertParse("all(group(" + image + "))", "all(group(" + image + "))");
        }
    }

    @Test
    void testTokenizedWhitespace() {
        String expected = "all(group(foo) each(output(max(bar))))";

        assertParse(" all(group(foo)each(output(max(bar))))", expected);
        assertIllegalArgument("all (group(foo)each(output(max(bar))))", "Encountered \" <SPACE> \" \"\" at line 1, column 4.");
        assertParse("all( group(foo)each(output(max(bar))))", expected);
        assertIllegalArgument("all(group (foo)each(output(max(bar))))", "Encountered \" <SPACE> \" \"\" at line 1, column 10.");
        assertParse("all(group( foo)each(output(max(bar))))", expected);
        assertParse("all(group(foo )each(output(max(bar))))", expected);
        assertParse("all(group(foo) each(output(max(bar))))", expected);
        assertIllegalArgument("all(group(foo)each (output(max(bar))))", "Encountered \" <SPACE> \" \"\" at line 1, column 19.");
        assertParse("all(group(foo)each( output(max(bar))))", expected);
        assertIllegalArgument("all(group(foo)each(output (max(bar))))", "Encountered \" <SPACE> \" \"\" at line 1, column 26.");
        assertParse("all(group(foo)each(output( max(bar))))", expected);
        assertParse("all(group(foo)each(output(max(bar))))", expected);
        assertParse("all(group(foo)each(output(max( bar))))", expected);
        assertParse("all(group(foo)each(output(max(bar ))))", expected);
        assertParse("all(group(foo)each(output(max(bar) )))", expected);
        assertParse("all(group(foo)each(output(max(bar)) ))", expected);
        assertParse("all(group(foo)each(output(max(bar))) )", expected);
        assertParse("all(group(foo)each(output(max(bar)))) ", expected);
    }

    @Test
    void testOperationTypes() {
        assertParse("all()");
        assertParse("each()");
        assertParse("all(each())");
        assertParse("each(all())");
        assertParse("all(all() all())");
        assertParse("all(all() each())");
        assertParse("all(each() all())");
        assertParse("all(each() each())");
        assertParse("each(all() all())");
        assertIllegalArgument("each(all() each())",
                "Operation 'each()' can not operate on single hit.");
        assertIllegalArgument("each(group(foo) all() each())",
                "Operation 'each(group(foo) all() each())' can not group single hit.");
        assertIllegalArgument("each(each() all())",
                "Operation 'each()' can not operate on single hit.");
        assertIllegalArgument("each(group(foo) each() all())",
                "Operation 'each(group(foo) each() all())' can not group single hit.");
        assertIllegalArgument("each(each() each())",
                "Operation 'each()' can not operate on single hit.");
        assertIllegalArgument("each(group(foo) each() each())",
                "Operation 'each(group(foo) each() each())' can not group single hit.");
    }

    @Test
    void testOperationParts() {
        assertParse("all(group(foo))");
        assertParse("all(hint(foo))");
        assertParse("all(hint(foo) hint(bar))");
        assertParse("all(max(1))");
        assertParse("all(order(foo))");
        assertParse("all(order(+foo))");
        assertParse("all(order(-foo))");
        assertParse("all(order(foo, bar))");
        assertParse("all(order(foo, +bar))");
        assertParse("all(order(foo, -bar))");
        assertParse("all(order(+foo, bar))");
        assertParse("all(order(+foo, +bar))");
        assertParse("all(order(+foo, -bar))");
        assertParse("all(order(-foo, bar))");
        assertParse("all(order(-foo, +bar))");
        assertParse("all(order(-foo, -bar))");
        assertParse("all(output(min(a)))");
        assertParse("all(output(min(a), min(b)))");
        assertParse("all(precision(1))");
        assertParse("all(where(foo))");
        assertParse("all(where($foo))");
    }

    @Test
    void testComplexExpressionTypes() {
        // fixedwidth
        assertParse("all(group(fixedwidth(foo, 1)))");
        assertParse("all(group(fixedwidth(foo, 1.2)))");

        // md5
        assertParse("all(group(md5(foo, 1)))");

        // predefined
        assertParse("all(group(predefined(foo, bucket(1, 2))))");
        assertParse("all(group(predefined(foo, bucket(-1, 2))))");
        assertParse("all(group(predefined(foo, bucket(-2, -1))))");
        assertParse("all(group(predefined(foo, bucket(1, 2), bucket(3, 4))))");
        assertParse("all(group(predefined(foo, bucket(1, 2), bucket(3, 4), bucket(5, 6))))");
        assertParse("all(group(predefined(foo, bucket(1, 2), bucket(2, 3), bucket(3, 4))))");
        assertParse("all(group(predefined(foo, bucket(-100, 0), bucket(0), bucket<0, 100))))");
        assertParse("all(group(predefined(foo, bucket[1, 2>, bucket[3, 4>)))");

        assertParse("all(group(predefined(foo, bucket[1, 2>)))");
        assertParse("all(group(predefined(foo, bucket[-1, 2>)))");
        assertParse("all(group(predefined(foo, bucket[-2, -1>)))");
        assertParse("all(group(predefined(foo, bucket[1, 2>, bucket(3, 4>)))");
        assertParse("all(group(predefined(foo, bucket[1, 2>, bucket[3, 4>, bucket[5, 6>)))");
        assertParse("all(group(predefined(foo, bucket[1, 2>, bucket[2, 3>, bucket[3, 4>)))");

        assertParse("all(group(predefined(foo, bucket<1, 5>)))");
        assertParse("all(group(predefined(foo, bucket[1, 5>)))");
        assertParse("all(group(predefined(foo, bucket<1, 5])))");
        assertParse("all(group(predefined(foo, bucket[1, 5])))");

        assertParse("all(group(predefined(foo, bucket<1, inf>)))");
        assertParse("all(group(predefined(foo, bucket<-inf, -1>)))");
        assertParse("all(group(predefined(foo, bucket<a, inf>)))");
        assertParse("all(group(predefined(foo, bucket<'a', inf>)))");
        assertParse("all(group(predefined(foo, bucket<-inf, a>)))");
        assertParse("all(group(predefined(foo, bucket[-inf, 'a'>)))");
        assertParse("all(group(predefined(foo, bucket<-inf, -0.3>)))");
        assertParse("all(group(predefined(foo, bucket<0.3, inf])))");
        assertParse("all(group(predefined(foo, bucket<0.3, inf])))");
        assertParse("all(group(predefined(foo, bucket<infinite, inf])))");
        assertParse("all(group(predefined(foo, bucket<myinf, inf])))");
        assertParse("all(group(predefined(foo, bucket<-inf, infinite])))");
        assertParse("all(group(predefined(foo, bucket<-inf, myinf])))");

        assertParse("all(group(predefined(foo, bucket(1.0, 2.0))))");
        assertParse("all(group(predefined(foo, bucket(1.0, 2.0), bucket(3.0, 4.0))))");
        assertParse("all(group(predefined(foo, bucket(1.0, 2.0), bucket(3.0, 4.0), bucket(5.0, 6.0))))");

        assertParse("all(group(predefined(foo, bucket<1.0, 2.0>)))");
        assertParse("all(group(predefined(foo, bucket[1.0, 2.0>)))");
        assertParse("all(group(predefined(foo, bucket<1.0, 2.0])))");
        assertParse("all(group(predefined(foo, bucket[1.0, 2.0])))");
        assertParse("all(group(predefined(foo, bucket[1.0, 2.0>, bucket[3.0, 4.0>)))");
        assertParse("all(group(predefined(foo, bucket[1.0, 2.0>, bucket[3.0, 4.0>, bucket[5.0, 6.0>)))");
        assertParse("all(group(predefined(foo, bucket[1.0, 2.0>, bucket[2.0], bucket<2.0, 6.0>)))");

        assertParse("all(group(predefined(foo, bucket('a', 'b'))))");
        assertParse("all(group(predefined(foo, bucket['a', 'b'>)))");
        assertParse("all(group(predefined(foo, bucket<'a', 'c'>)))");
        assertParse("all(group(predefined(foo, bucket<'a', 'b'])))");
        assertParse("all(group(predefined(foo, bucket['a', 'b'])))");
        assertParse("all(group(predefined(foo, bucket('a', 'b'), bucket('c', 'd'))))");
        assertParse("all(group(predefined(foo, bucket('a', 'b'), bucket('c', 'd'), bucket('e', 'f'))))");

        assertParse("all(group(predefined(foo, bucket(\"a\", \"b\"))))");
        assertParse("all(group(predefined(foo, bucket(\"a\", \"b\"), bucket(\"c\", \"d\"))))");
        assertParse("all(group(predefined(foo, bucket(\"a\", \"b\"), bucket(\"c\", \"d\"), bucket(\"e\", \"f\"))))");

        assertParse("all(group(predefined(foo, bucket(a, b))))");
        assertParse("all(group(predefined(foo, bucket(a, b), bucket(c, d))))");
        assertParse("all(group(predefined(foo, bucket(a, b), bucket(c, d), bucket(e, f))))");
        assertParse("all(group(predefined(foo, bucket(a, b), bucket(c), bucket(e, f))))");

        assertParse("all(group(predefined(foo, bucket('a', \"b\"))))");
        assertParse("all(group(predefined(foo, bucket('a', \"b\"), bucket(c, 'd'))))");
        assertParse("all(group(predefined(foo, bucket('a', \"b\"), bucket(c, 'd'), bucket(\"e\", f))))");

        assertParse("all(group(predefined(foo, bucket('a(', \"b)\"), bucket(c, 'd()'))))");
        assertParse("all(group(predefined(foo, bucket({2}, {6}), bucket({7}, {12}))))");
        assertParse("all(group(predefined(foo, bucket({0, 2}, {0, 6}), bucket({0, 7}, {0, 12}))))");
        assertParse("all(group(predefined(foo, bucket({'b', 'a'}, {'k', 'a'}), bucket({'k', 'a'}, {'u', 'b'}))))");

        assertIllegalArgument("all(group(predefined(foo, bucket(1, 2.0))))",
                "Bucket type mismatch, expected 'LongValue' got 'DoubleValue'.");
        assertIllegalArgument("all(group(predefined(foo, bucket(1, '2'))))",
                "Bucket type mismatch, expected 'LongValue' got 'StringValue'.");
        assertIllegalArgument("all(group(predefined(foo, bucket(1, 2), bucket(3.0, 4.0))))",
                "Bucket type mismatch, expected 'LongValue' got 'DoubleValue'.");
        assertIllegalArgument("all(group(predefined(foo, bucket(1, 2), bucket('3', '4'))))",
                "Bucket type mismatch, expected 'LongValue' got 'StringValue'.");
        assertIllegalArgument("all(group(predefined(foo, bucket(1, 2), bucket(\"3\", \"4\"))))",
                "Bucket type mismatch, expected 'LongValue' got 'StringValue'.");
        assertIllegalArgument("all(group(predefined(foo, bucket(1, 2), bucket(three, four))))",
                "Bucket type mismatch, expected 'LongValue' got 'StringValue'.");
        assertIllegalArgument("all(group(predefined(foo, bucket<-inf, inf>)))",
                "Bucket type mismatch, cannot both be infinity");
        assertIllegalArgument("all(group(predefined(foo, bucket<inf, -inf>)))",
                "Encountered \" \"inf\" \"inf\"\" at line 1, column 34.");

        assertIllegalArgument("all(group(predefined(foo, bucket(2, 1))))",
                "Bucket to-value can not be less than from-value.");
        assertIllegalArgument("all(group(predefined(foo, bucket(3, 4), bucket(1, 2))))",
                "Buckets must be monotonically increasing, got bucket[3, 4> before bucket[1, 2>.");
        assertIllegalArgument("all(group(predefined(foo, bucket(b, a))))",
                "Bucket to-value can not be less than from-value.");
        assertIllegalArgument("all(group(predefined(foo, bucket(b, -inf))))",
                "Encountered \" \"-inf\" \"-inf\"\" at line 1, column 37.");
        assertIllegalArgument("all(group(predefined(foo, bucket(c, d), bucket(a, b))))",
                "Buckets must be monotonically increasing, got bucket[\"c\", \"d\"> before bucket[\"a\", \"b\">.");
        assertIllegalArgument("all(group(predefined(foo, bucket(c, d), bucket(-inf, e))))",
                "Buckets must be monotonically increasing, got bucket[\"c\", \"d\"> before bucket[-inf, \"e\">.");
        assertIllegalArgument("all(group(predefined(foo, bucket(u, inf), bucket(e, i))))",
                "Buckets must be monotonically increasing, got bucket[\"u\", inf> before bucket[\"e\", \"i\">.");

        // xorbit
        assertParse("all(group(xorbit(foo, 1)))");
    }

    @Test
    void testInfixArithmetic() {
        assertParse("all(group(1))", "all(group(1))");
        assertParse("all(group(1+2))", "all(group(add(1, 2)))");
        assertParse("all(group(1-2))", "all(group(sub(1, 2)))");
        assertParse("all(group(1*2))", "all(group(mul(1, 2)))");
        assertParse("all(group(1/2))", "all(group(div(1, 2)))");
        assertParse("all(group(1%2))", "all(group(mod(1, 2)))");
        assertParse("all(group(1+2+3))", "all(group(add(add(1, 2), 3)))");
        assertParse("all(group(1+2-3))", "all(group(add(1, sub(2, 3))))");
        assertParse("all(group(1+2*3))", "all(group(add(1, mul(2, 3))))");
        assertParse("all(group(1+2/3))", "all(group(add(1, div(2, 3))))");
        assertParse("all(group(1+2%3))", "all(group(add(1, mod(2, 3))))");
        assertParse("all(group((1+2)+3))", "all(group(add(add(1, 2), 3)))");
        assertParse("all(group((1+2)-3))", "all(group(sub(add(1, 2), 3)))");
        assertParse("all(group((1+2)*3))", "all(group(mul(add(1, 2), 3)))");
        assertParse("all(group((1+2)/3))", "all(group(div(add(1, 2), 3)))");
        assertParse("all(group((1+2)%3))", "all(group(mod(add(1, 2), 3)))");
    }

    @Test
    void testOperationLabel() {
        assertParse("each() as(foo)",
                "each() as(foo)");
        assertParse("all(each() as(foo)" +
                "    each() as(bar))",
                "all(each() as(foo) each() as(bar))");
        assertParse("all(group(a) each(each() as(foo)" +
                "                  each() as(bar))" +
                "             each() as(baz))",
                "all(group(a) each(each() as(foo) each() as(bar)) each() as(baz))");

        assertIllegalArgument("all() as(foo)", "Encountered \" \"as\" \"as\"\" at line 1, column 7.");
        assertIllegalArgument("all(all() as(foo))", "Encountered \" \"as\" \"as\"\" at line 1, column 11.");
        assertIllegalArgument("each(all() as(foo))", "Encountered \" \"as\" \"as\"\" at line 1, column 12.");
    }

    @Test
    void testAttributeName() {
        assertParse("all(group(foo))");
        assertIllegalArgument("all(group(foo.))",
                "Encountered \" \")\" \")\"\" at line 1, column 15.");
        assertParse("all(group(foo.bar))");
        assertIllegalArgument("all(group(foo.bar.))",
                "Encountered \" \")\" \")\"\" at line 1, column 19.");
        assertParse("all(group(foo.bar.baz))");
    }

    @Test
    void testOutputLabel() {
        assertParse("all(output(min(a) as(foo)))",
                "all(output(min(a) as(foo)))");
        assertParse("all(output(min(a) as(foo), max(b) as(bar)))",
                "all(output(min(a) as(foo), max(b) as(bar)))");

        assertIllegalArgument("all(output(min(a)) as(foo))",
                "Encountered \" \"as\" \"as\"\" at line 1, column 20.");
    }

    @Test
    void testRootWhere() {
        String expected = "all(where(bar) all(group(foo)))";
        assertParse("all(where(bar) all(group(foo)))", expected);
        assertParse("all(group(foo)) where(bar)", expected);
    }

    @Test
    void testParseBadRequest() {
        assertIllegalArgument("output(count())",
                "Encountered \" \"output\" \"output\"\" at line 1, column 1.");
        assertIllegalArgument("each(output(count()))",
                "Expression 'count()' not applicable for single hit.");
        assertIllegalArgument("all(output(count())))",
                "Encountered \" \")\" \")\"\" at line 1, column 21.");
    }

    @Test
    void testAttributeFunction() {
        assertParse("all(group(attribute(foo)))");
        assertParse("all(group(attribute(foo)) order(sum(attribute(a))))");
    }

    @Test
    void testAccuracy() {
        assertParse("all(accuracy(0.5))");
        assertParse("all(group(foo) accuracy(1.0))");
    }

    @Test
    void testMapSyntax() {
        assertParse("all(group(my.little{key}))", "all(group(my.little{\"key\"}))");
        assertParse("all(group(my.little{key }))", "all(group(my.little{\"key\"}))");
        assertParse("all(group(my.little{\"key\"}))", "all(group(my.little{\"key\"}))");
        assertParse("all(group(my.little{\"key{}%\"}))", "all(group(my.little{\"key{}%\"}))");
        assertParse("all(group(my.little{key}.name))", "all(group(my.little{\"key\"}.name))");
        assertParse("all(group(my.little{key }.name))", "all(group(my.little{\"key\"}.name))");
        assertParse("all(group(my.little{\"key\"}.name))", "all(group(my.little{\"key\"}.name))");
        assertParse("all(group(my.little{\"key{}%\"}.name))", "all(group(my.little{\"key{}%\"}.name))");

        assertAttributeMapLookup("all(group(my_map{\"my_key\"}))",
                "my_map.key", "my_map.value", "my_key", "");
        assertAttributeMapLookup("all(group(my_map{\"my_key\"}.name))",
                "my_map.key", "my_map.value.name", "my_key", "");
        assertAttributeMapLookup("all(group(my.map{\"my_key\"}))",
                "my.map.key", "my.map.value", "my_key", "");
    }

    @Test
    void testMapSyntaxWithKeySourceAttribute() {
        assertAttributeMapLookup("all(group(my_map{attribute(my_attr)}))",
                "my_map.key", "my_map.value", "", "my_attr");
        assertAttributeMapLookup("all(group(my_map{attribute(my_attr)}.name))",
                "my_map.key", "my_map.value.name", "", "my_attr");
        assertAttributeMapLookup("all(group(my.map{attribute(my_attr.name)}))",
                "my.map.key", "my.map.value", "", "my_attr.name");

        assertIllegalArgument("all(group(my_map{attribute(\"my_attr\")}))",
                "Encountered \" <STRING> \"\\\"my_attr\\\"\"\" at line 1, column 28");

    }

    private static void assertAttributeMapLookup(String request,
                                                 String expKeyAttribute,
                                                 String expValueAttribute,
                                                 String expKey,
                                                 String expKeySourceAttribute) {
        assertParse(request, request);
        List<GroupingOperation> operations = GroupingOperation.fromStringAsList(request);
        assertEquals(1, operations.size());
        AttributeMapLookupValue mapLookup = (AttributeMapLookupValue)operations.get(0).getGroupBy();
        assertEquals(expKeyAttribute, mapLookup.getKeyAttribute());
        assertEquals(expValueAttribute, mapLookup.getValueAttribute());
        assertEquals(expKey, mapLookup.getKey());
        assertEquals(expKeySourceAttribute, mapLookup.getKeySourceAttribute());
    }

    @Test
    void testMisc() {
        for (String fnc : Arrays.asList("time.date",
                                        "time.dayofmonth",
                                        "time.dayofweek",
                                        "time.dayofyear",
                                        "time.hourofday",
                                        "time.minuteofhour",
                                        "time.monthofyear",
                                        "time.secondofminute",
                                        "time.year")) {
            assertParse("each(output(" + fnc + "(foo)))");
        }

        assertParse("all(group(artist) max(7))");
        assertParse("all(max(76) all(group(artist) max(7)))");
        assertParse("all(group(artist) max(7) where(true))");
        assertParse("all(group(artist) order(sum(a)) output(count()))");
        assertParse("all(group(artist) order(+sum(a)) output(count()))");
        assertParse("all(group(artist) order(-sum(a)) output(count()))");
        assertParse("all(group(artist) order(-sum(a), +xor(b)) output(count()))");
        assertParse("all(group(artist) max(1) output(count()))");
        assertParse("all(group(-(m))  max(1) output(count()))");
        assertParse("all(group(min) max(1) output(count()))");
        assertParse("all(group(artist) max(2) each(each(output(summary()))))");
        assertParse("all(group(artist) max(2) each(each(output(summary(simple)))))");
        assertParse("all(group(artist) max(5) each(output(count()) each(output(summary()))))");
        assertParse("all(group(strlen(attr)))");
        assertParse("all(group(normalizesubject(attr)))");
        assertParse("all(group(strcat(attr, attr2)))");
        assertParse("all(group(tostring(attr)))");
        assertParse("all(group(toraw(attr)))");
        assertParse("all(group(zcurve.x(attr)))");
        assertParse("all(group(zcurve.y(attr)))");
        assertParse("all(group(uca(attr, \"foo\")))");
        assertParse("all(group(uca(attr, \"foo\", \"PRIMARY\")))");
        assertParse("all(group(uca(attr, \"foo\", \"SECONDARY\")))");
        assertParse("all(group(uca(attr, \"foo\", \"TERTIARY\")))");
        assertParse("all(group(uca(attr, \"foo\", \"QUATERNARY\")))");
        assertParse("all(group(uca(attr, \"foo\", \"IDENTICAL\")))");
        assertIllegalArgument("all(group(uca(attr, \"foo\", \"foo\")))", "Not a valid UCA strength: foo");
        assertParse("all(group(tolong(attr)))");
        assertParse("all(group(sort(attr)))");
        assertParse("all(group(reverse(attr)))");
        assertParse("all(group(docidnsspecific()))");
        assertParse("all(group(relevance()))");
        // TODO: assertParseRequest("all(group(a) each(output(xor(md5(b)) xor(md5(b, 0, 64)))))");
        // TODO: assertParseRequest("all(group(a) each(output(xor(xorbit(b)) xor(xorbit(b, 64)))))");
        assertParse("all(group(artist) each(each(output(summary()))))");
        assertParse("all(group(artist) max(13) each(group(fixedwidth(year, 21.34)) max(55) output(count()) " +
                "each(each(output(summary())))))");
        assertParse("all(group(artist) max(13) each(group(predefined(year, bucket(7, 19), bucket(90, 300))) " +
                "max(55) output(count()) each(each(output(summary())))))");
        assertParse("all(group(artist) max(13) each(group(predefined(year, bucket(7.1, 19.0), bucket(90.7, 300.0))) " +
                "max(55) output(count()) each(each(output(summary())))))");
        assertParse("all(group(artist) max(13) each(group(predefined(year, bucket('a', 'b'), bucket('cd'))) " +
                "max(55) output(count()) each(each(output(summary())))))");

        assertParse("all(output(count()))");
        assertParse("all(group(album) output(count()))");
        assertParse("all(group(album) each(output(count())))");
        assertParse("all(group(artist) each(group(album) output(count()))" +
                "                  each(group(song) output(count())))");
        assertParse("all(group(artist) output(count())" +
                "    each(group(album) output(count())" +
                "         each(group(song) output(count())" +
                "              each(each(output(summary()))))))");
        assertParse("all(group(album) order(-$total=sum(length)) each(output($total)))");
        assertParse("all(group(album) max(1) each(output(sum(length))))");
        assertParse("all(group(artist) each(max(2) each(output(summary()))))");
        assertParse("all(group(artist) max(3)" +
                "    each(group(album as(albumsongs)) each(each(output(summary()))))" +
                "    each(group(album as(albumlength)) output(sum(sum(length)))))");
        assertParse("all(group(artist) max(15)" +
                "    each(group(album) " +
                "         each(group(song)" +
                "              each(max(2) each(output(summary()))))))");
        assertParse("all(group(artist) max(15)" +
                "    each(group(album)" +
                "         each(group(song)" +
                "              each(max(2) each(output(summary())))))" +
                "    each(group(song) max(5) order(sum(popularity))" +
                "         each(output(sum(sold)) each(output(summary())))))");

        assertParse("all(group(artist) order(max(relevance) * count()) each(output(count())))");
        assertParse("all(group(artist) each(output(sum(popularity) / count())))");
        assertParse("all(group(artist) accuracy(0.1) each(output(sum(popularity) / count())))");
        assertParse("all(group(debugwait(artist, 3.3, true)))");
        assertParse("all(group(debugwait(artist, 3.3, false)))");
        assertIllegalArgument("all(group(debugwait(artist, -3.3, true)))",
                "Encountered \" \"-\" \"-\"\" at line 1, column 29");
        assertIllegalArgument("all(group(debugwait(artist, 3.3, lol)))",
                "Encountered \" <IDENTIFIER> \"lol\"\" at line 1, column 34");
        assertParse("all(group(artist) each(output(stddev(simple))))");

        // Test max()
        assertTrue(assertParse("all(group(artist) max(inf))").get(0).hasUnlimitedMax());
        assertEquals(1, assertParse("all(group(artist) max(1))").get(0).getMax());
        assertFalse(assertParse("all(group(artist))").get(0).hasMax());
    }

    @Test
    void testBucket() {
        List<GroupingOperation> operations = assertParse("all(group(predefined(artist, bucket('a'), bucket('c', 'z'))))");
        assertEquals(1, operations.size());
        assertEquals("all(group(predefined(artist, bucket[\"a\", \"a \">, bucket[\"c\", \"z\">)))",
                operations.get(0).toString());
    }

    @Test
    void requireThatParseExceptionMessagesContainErrorMarker() {
        assertIllegalArgument("foo",
                "Encountered \" <IDENTIFIER> \"foo\"\" at line 1, column 1.\n\n" +
                        "Was expecting one of:\n\n" +
                        "<SPACE> ...\n" +
                        "    \"all\" ...\n" +
                        "    \"each\" ...\n" +
                        "    \n" +
                        "At position:\n" +
                        "foo\n" +
                        "^");
        assertIllegalArgument("\n foo",
                "Encountered \" <IDENTIFIER> \"foo\"\" at line 2, column 2.\n\n" +
                        "Was expecting one of:\n\n" +
                        "<SPACE> ...\n" +
                        "    \"all\" ...\n" +
                        "    \"each\" ...\n" +
                        "    \n" +
                        "At position:\n" +
                        " foo\n" +
                        " ^");
    }

    // --------------------------------------------------------------------------------
    // Utilities.
    // --------------------------------------------------------------------------------

    private static List<GroupingOperation> assertParse(String request, String... expectedOperations) {
        List<GroupingOperation> operations = GroupingOperation.fromStringAsList(request);
        List<String> actual = new ArrayList<>(operations.size());
        for (GroupingOperation operation : operations) {
            operation.resolveLevel(1);
            actual.add(operation.toString());
        }
        if (expectedOperations.length > 0) {
            assertEquals(Arrays.asList(expectedOperations), actual);
        }

        // make sure that operation does not mutate through toString() -> fromString()
        for (GroupingOperation operation : operations) {
            assertEquals(operation.toString(), GroupingOperation.fromString(operation.toString()).toString());
        }

        // make sure that yql+ is capable of handling request
        assertYqlParsable(request, expectedOperations);
        return operations;
    }

    private static void assertYqlParsable(String request, String... expectedOperations) {
        YqlParser parser = new YqlParser(new ParserEnvironment());
        parser.parse(new Parsable().setQuery("select foo from bar where baz contains 'baz' | " + request));
        List<VespaGroupingStep> steps = parser.getGroupingSteps();
        List<String> actual = new ArrayList<>(steps.size());
        for (VespaGroupingStep step : steps) {
            actual.add(step.getOperation().toString());
        }
        if (expectedOperations.length > 0) {
            assertEquals(Arrays.asList(expectedOperations), actual);
        }
    }

    private static void assertIllegalArgument(String request, String expectedException) {
        try {
            GroupingOperation.fromString(request).resolveLevel(1);
            fail("Expected: " + expectedException);
        } catch (IllegalArgumentException e) {
            if (! e.getMessage().startsWith(expectedException)) {
                fail("Expected exception with message starting with:\n'" + expectedException + ", but got:\n'" + e.getMessage());
            }
        }
    }

}
