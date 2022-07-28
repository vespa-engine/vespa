// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.grouping.request.parser;

import com.yahoo.search.grouping.request.GroupingOperation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class GroupingParserBenchmarkTest {

    private static final int NUM_RUNS = 10;
    private static final Map<String, Long> PREV_RESULTS = new LinkedHashMap<>();

    static {
        PREV_RESULTS.put("Original", 79276393L);
        PREV_RESULTS.put("NoCache", 71728602L);
        PREV_RESULTS.put("CharStream", 43852348L);
        PREV_RESULTS.put("CharArray", 22936513L);
    }

    @Test
    void requireThatGroupingParserIsFast() {
        List<String> inputs = getInputs();
        long ignore = 0;
        long now = 0;
        for (int i = 0; i < 2; ++i) {
            now = System.nanoTime();
            for (int j = 0; j < NUM_RUNS; ++j) {
                for (String str : inputs) {
                    ignore += parseRequest(str);
                }
            }
        }
        long micros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - now);
        System.out.format("%d \u03bcs (avg %.2f)\n", micros, (double) micros / (NUM_RUNS * inputs.size()));
        for (Map.Entry<String, Long> entry : PREV_RESULTS.entrySet()) {
            System.out.format("%-20s : %4.2f\n", entry.getKey(), (double) micros / entry.getValue());
        }
        System.out.println("\nignore " + ignore);
    }

    private static int parseRequest(String str) {
        return GroupingOperation.fromStringAsList(str).size();
    }

    private static List<String> getInputs() {
        return Arrays.asList(
                " all(group(foo)each(output(max(bar))))",
                "all( group(foo)each(output(max(bar))))",
                "all(group( foo)each(output(max(bar))))",
                "all(group(foo )each(output(max(bar))))",
                "all(group(foo) each(output(max(bar))))",
                "all(group(foo)each( output(max(bar))))",
                "all(group(foo)each(output( max(bar))))",
                "all(group(foo)each(output(max(bar))))",
                "all(group(foo)each(output(max( bar))))",
                "all(group(foo)each(output(max(bar ))))",
                "all(group(foo)each(output(max(bar) )))",
                "all(group(foo)each(output(max(bar)) ))",
                "all(group(foo)each(output(max(bar))) )",
                "all(group(foo)each(output(max(bar)))) ",
                "all()",
                "each()",
                "all(each())",
                "each(all())",
                "all(all() all())",
                "all(all() each())",
                "all(each() all())",
                "all(each() each())",
                "each(all() all())",
                "all(group(foo))",
                "all(max(1))",
                "all(order(foo))",
                "all(order(+foo))",
                "all(order(-foo))",
                "all(order(foo, bar))",
                "all(order(foo, +bar))",
                "all(order(foo, -bar))",
                "all(order(+foo, bar))",
                "all(order(+foo, +bar))",
                "all(order(+foo, -bar))",
                "all(order(-foo, bar))",
                "all(order(-foo, +bar))",
                "all(order(-foo, -bar))",
                "all(output(min(a)))",
                "all(output(min(a), min(b)))",
                "all(precision(1))",
                "all(where(foo))",
                "all(where($foo))",
                "all(group(fixedwidth(foo, 1)))",
                "all(group(fixedwidth(foo, 1.2)))",
                "all(group(md5(foo, 1)))",
                "all(group(predefined(foo, bucket(1, 2))))",
                "all(group(predefined(foo, bucket(-1, 2))))",
                "all(group(predefined(foo, bucket(-2, -1))))",
                "all(group(predefined(foo, bucket(1, 2), bucket(3, 4))))",
                "all(group(predefined(foo, bucket(1, 2), bucket(3, 4), bucket(5, 6))))",
                "all(group(predefined(foo, bucket(1, 2), bucket(2, 3), bucket(3, 4))))",
                "all(group(predefined(foo, bucket(-100, 0), bucket(0), bucket<0, 100))))",
                "all(group(predefined(foo, bucket[1, 2>)))",
                "all(group(predefined(foo, bucket[-1, 2>)))",
                "all(group(predefined(foo, bucket[-2, -1>)))",
                "all(group(predefined(foo, bucket[1, 2>, bucket(3, 4>)))",
                "all(group(predefined(foo, bucket[1, 2>, bucket[3, 4>, bucket[5, 6>)))",
                "all(group(predefined(foo, bucket[1, 2>, bucket[2, 3>, bucket[3, 4>)))",
                "all(group(predefined(foo, bucket<1, 5>)))",
                "all(group(predefined(foo, bucket[1, 5>)))",
                "all(group(predefined(foo, bucket<1, 5])))",
                "all(group(predefined(foo, bucket[1, 5])))",
                "all(group(predefined(foo, bucket<1, inf>)))",
                "all(group(predefined(foo, bucket<-inf, -1>)))",
                "all(group(predefined(foo, bucket<a, inf>)))",
                "all(group(predefined(foo, bucket<'a', inf>)))",
                "all(group(predefined(foo, bucket<-inf, a>)))",
                "all(group(predefined(foo, bucket[-inf, 'a'>)))",
                "all(group(predefined(foo, bucket<-inf, -0.3>)))",
                "all(group(predefined(foo, bucket<0.3, inf])))",
                "all(group(predefined(foo, bucket<0.3, inf])))",
                "all(group(predefined(foo, bucket<infinite, inf])))",
                "all(group(predefined(foo, bucket<myinf, inf])))",
                "all(group(predefined(foo, bucket<-inf, infinite])))",
                "all(group(predefined(foo, bucket<-inf, myinf])))",
                "all(group(predefined(foo, bucket(1.0, 2.0))))",
                "all(group(predefined(foo, bucket(1.0, 2.0), bucket(3.0, 4.0))))",
                "all(group(predefined(foo, bucket(1.0, 2.0), bucket(3.0, 4.0), bucket(5.0, 6.0))))",
                "all(group(predefined(foo, bucket<1.0, 2.0>)))",
                "all(group(predefined(foo, bucket[1.0, 2.0>)))",
                "all(group(predefined(foo, bucket<1.0, 2.0])))",
                "all(group(predefined(foo, bucket[1.0, 2.0])))",
                "all(group(predefined(foo, bucket[1.0, 2.0>, bucket[3.0, 4.0>)))",
                "all(group(predefined(foo, bucket[1.0, 2.0>, bucket[3.0, 4.0>, bucket[5.0, 6.0>)))",
                "all(group(predefined(foo, bucket[1.0, 2.0>, bucket[2.0], bucket<2.0, 6.0>)))",
                "all(group(predefined(foo, bucket('a', 'b'))))",
                "all(group(predefined(foo, bucket['a', 'b'>)))",
                "all(group(predefined(foo, bucket<'a', 'c'>)))",
                "all(group(predefined(foo, bucket<'a', 'b'])))",
                "all(group(predefined(foo, bucket['a', 'b'])))",
                "all(group(predefined(foo, bucket('a', 'b'), bucket('c', 'd'))))",
                "all(group(predefined(foo, bucket('a', 'b'), bucket('c', 'd'), bucket('e', 'f'))))",
                "all(group(predefined(foo, bucket(\"a\", \"b\"))))",
                "all(group(predefined(foo, bucket(\"a\", \"b\"), bucket(\"c\", \"d\"))))",
                "all(group(predefined(foo, bucket(\"a\", \"b\"), bucket(\"c\", \"d\"), bucket(\"e\", \"f\"))))",
                "all(group(predefined(foo, bucket(a, b))))",
                "all(group(predefined(foo, bucket(a, b), bucket(c, d))))",
                "all(group(predefined(foo, bucket(a, b), bucket(c, d), bucket(e, f))))",
                "all(group(predefined(foo, bucket(a, b), bucket(c), bucket(e, f))))",
                "all(group(predefined(foo, bucket('a', \"b\"))))",
                "all(group(predefined(foo, bucket('a', \"b\"), bucket(c, 'd'))))",
                "all(group(predefined(foo, bucket('a', \"b\"), bucket(c, 'd'), bucket(\"e\", f))))",
                "all(group(predefined(foo, bucket('a(', \"b)\"), bucket(c, 'd()'))))",
                "all(group(predefined(foo, bucket({2}, {6}), bucket({7}, {12}))))",
                "all(group(predefined(foo, bucket({0, 2}, {0, 6}), bucket({0, 7}, {0, 12}))))",
                "all(group(predefined(foo, bucket({'b', 'a'}, {'k', 'a'}), bucket({'k', 'a'}, {'u', 'b'}))))",
                "all(group(xorbit(foo, 1)))",
                "all(group(1))",
                "all(group(1+2))",
                "all(group(1-2))",
                "all(group(1*2))",
                "all(group(1/2))",
                "all(group(1%2))",
                "all(group(1+2+3))",
                "all(group(1+2-3))",
                "all(group(1+2*3))",
                "all(group(1+2/3))",
                "all(group(1+2%3))",
                "all(group((1+2)+3))",
                "all(group((1+2)-3))",
                "all(group((1+2)*3))",
                "all(group((1+2)/3))",
                "all(group((1+2)%3))",
                "each() as(foo)",
                "all(each() as(foo) each() as(bar))",
                "all(group(a) each(each() as(foo) each() as(bar)) each() as(baz))",
                "all(output(min(a) as(foo)))",
                "all(output(min(a) as(foo), max(b) as(bar)))",
                "all(where(bar) all(group(foo)))",
                "all(group(foo)) where(bar)",
                "all(group(attribute(foo)))",
                "all(group(attribute(foo)) order(sum(attribute(a))))",
                "all(accuracy(0.5))",
                "all(group(foo) accuracy(1.0))",
                "all(group(my.little{key}))", "all(group(my.little{\"key\"}))",
                "all(group(my.little{key }))", "all(group(my.little{\"key\"}))",
                "all(group(my.little{\"key\"}))", "all(group(my.little{\"key\"}))",
                "all(group(my.little{\"key{}%\"}))", "all(group(my.little{\"key{}%\"}))",
                "all(group(artist) max(7))",
                "all(max(76) all(group(artist) max(7)))",
                "all(group(artist) max(7) where(true))",
                "all(group(artist) order(sum(a)) output(count()))",
                "all(group(artist) order(+sum(a)) output(count()))",
                "all(group(artist) order(-sum(a)) output(count()))",
                "all(group(artist) order(-sum(a), +xor(b)) output(count()))",
                "all(group(artist) max(1) output(count()))",
                "all(group(-(m))  max(1) output(count()))",
                "all(group(min) max(1) output(count()))",
                "all(group(artist) max(2) each(each(output(summary()))))",
                "all(group(artist) max(2) each(each(output(summary(simple)))))",
                "all(group(artist) max(5) each(output(count()) each(output(summary()))))",
                "all(group(strlen(attr)))",
                "all(group(normalizesubject(attr)))",
                "all(group(strcat(attr, attr2)))",
                "all(group(tostring(attr)))",
                "all(group(toraw(attr)))",
                "all(group(zcurve.x(attr)))",
                "all(group(zcurve.y(attr)))",
                "all(group(uca(attr, \"foo\")))",
                "all(group(uca(attr, \"foo\", \"PRIMARY\")))",
                "all(group(uca(attr, \"foo\", \"SECONDARY\")))",
                "all(group(uca(attr, \"foo\", \"TERTIARY\")))",
                "all(group(uca(attr, \"foo\", \"QUATERNARY\")))",
                "all(group(uca(attr, \"foo\", \"IDENTICAL\")))",
                "all(group(tolong(attr)))",
                "all(group(sort(attr)))",
                "all(group(reverse(attr)))",
                "all(group(docidnsspecific()))",
                "all(group(relevance()))",
                "all(group(artist) each(each(output(summary()))))",
                "all(group(artist) max(13) " +
                "    each(group(fixedwidth(year, 21.34)) max(55) output(count()) " +
                "         each(each(output(summary())))))",
                "all(group(artist) max(13) " +
                "    each(group(predefined(year, bucket(7, 19), bucket(90, 300))) max(55) output(count()) " +
                "         each(each(output(summary())))))",
                "all(group(artist) max(13) " +
                "    each(group(predefined(year, bucket(7.1, 19.0), bucket(90.7, 300.0))) max(55) output(count()) " +
                "         each(each(output(summary())))))",
                "all(group(artist) max(13) " +
                "    each(group(predefined(year, bucket('a', 'b'), bucket('peder', 'pedersen'))) " +
                "         max(55) output(count()) " +
                "         each(each(output(summary())))))",
                "all(output(count()))",
                "all(group(album) output(count()))",
                "all(group(album) each(output(count())))",
                "all(group(artist) each(group(album) output(count()))" +
                "                  each(group(song) output(count())))",
                "all(group(artist) output(count())" +
                "    each(group(album) output(count())" +
                "         each(group(song) output(count())" +
                "              each(each(output(summary()))))))",
                "all(group(album) order(-$total=sum(length)) each(output($total)))",
                "all(group(album) max(1) each(output(sum(length))))",
                "all(group(artist) each(max(2) each(output(summary()))))",
                "all(group(artist) max(3)" +
                "    each(group(album as(albumsongs)) each(each(output(summary()))))" +
                "    each(group(album as(albumlength)) output(sum(sum(length)))))",
                "all(group(artist) max(15)" +
                "    each(group(album) " +
                "         each(group(song)" +
                "              each(max(2) each(output(summary()))))))",
                "all(group(artist) max(15)" +
                "    each(group(album)" +
                "         each(group(song)" +
                "              each(max(2) each(output(summary())))))" +
                "    each(group(song) max(5) order(sum(popularity))" +
                "         each(output(sum(sold)) each(output(summary())))))",
                "all(group(artist) order(max(relevance) * count()) each(output(count())))",
                "all(group(artist) each(output(sum(popularity) / count())))",
                "all(group(artist) accuracy(0.1) each(output(sum(popularity) / count())))",
                "all(group(debugwait(artist, 3.3, true)))",
                "all(group(debugwait(artist, 3.3, false)))");
    }
}
