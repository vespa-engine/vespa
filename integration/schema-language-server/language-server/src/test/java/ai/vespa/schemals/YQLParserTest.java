package ai.vespa.schemals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.schemadocument.YQLDocument;
import ai.vespa.schemals.schemadocument.YQLDocument.ParseResult;

import ai.vespa.schemals.testutils.*;
import ai.vespa.schemals.tree.YQLNode;

public class YQLParserTest {

    ParseResult parseString(String input, String fileName) throws Exception {
        ParseContext context = Utils.createTestContext(input, fileName);
        context.useVespaGroupingIdentifiers();
        return YQLDocument.parseContent(context);
    }

    ParseResult parseString(String input) throws Exception {
        return parseString(input, "/tmp/yql");
    }

    void checkQueryParses(int expectedErrors, String input) throws Exception {
        try {
            var parseResult = parseString(input);
            String testMessage = "For input: " + input + Utils.constructDiagnosticMessage(parseResult.diagnostics(), 1);
            assertEquals(expectedErrors, Utils.countErrors(parseResult.diagnostics()), testMessage);
    
            if (expectedErrors == 0) {
                assertTrue(parseResult.CST().isPresent(), "Expected that a YQLNode was present in the input: " + input);

                YQLNode node = parseResult.CST().get();
                int charsRead = node.getEndOffset();
                assertEquals(input.length(), charsRead, "Expected the parser to read all the chars in the input, but it read " + charsRead + " of " + input.length() + " for input: " + input);
            }
        } catch (Exception e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
            throw new Exception(input + "\n" + e.getMessage() + "\n" + stringWriter.toString());
        }
    }

    @TestFactory
    Stream<DynamicTest> generateGoodTests() {
        String[] groupingQueries = new String[] {

            // From docs: /en/grouping.html
            "all( group(customer) each(output(sum(price))) )",
            "all(group(customer) max(2) precision(12) order(-count()) each(output(sum(price))))",
            "all(group(customer) each(max(3) each(output(summary()))))",
            "all(group(a) max(5) each(output(count())))",
            "all(group(a) max(5) each(output(count()) max(7) each(output(summary()))))",
            "all(all(group(a) max(3) each(output(count()) max(5) each(output(summary())))) all(group(b) max(3) each(output(count()) max(5) each(output(summary())))))",
            "all(group(a) max(5) each(output(count()) max(7) each(output(summary()))))",
            "all(group(a) each(output(count()) each(output(summary()))))",
            "all(group(customer) each(group(time.date(date)) each(output(sum(price)))))",
            "all(group(customer) each(max(1) output(sum(price)) each(output(summary()))) each(group(time.date(date)) each(max(10) output(sum(price)) each(output(summary())))))",
            "all(group(price) each(each(output(summary()))))",
            "all(group(price/1000) each(each(output(summary()))))",
            "all(group(fixedwidth(price,1000)) each(each(output(summary()))))",
            "all(group(predefined(price, bucket(0,1000), bucket(1000,2000), bucket(2000,5000), bucket(5000,inf))) each(each(output(summary()))))",
            "all(group(predefined(price, bucket[0,1000>, bucket[1000,2000>, bucket[2000,5000>, bucket[5000,inf>)) each(each(output(summary()))))",
            "all(group(predefined(customer, bucket(-inf,\"Jones\"), bucket(\"Jones\", inf))) each(each(output(summary()))))",
            "all(group(predefined(customer, bucket<-inf,\"Jones\">, bucket[\"Jones\"], bucket<\"Jones\", inf>)) each(each(output(summary()))))",
            "all(group(predefined(tax, bucket[0.0,0.2>, bucket[0.2,0.5>, bucket[0.5,inf>)) each(each(output(summary()))))",
            "{ 'continuations':['BGAAABEBCA'] }all(output(count()))",
            "{ 'continuations':['BGAAABEBCA', 'BGAAABEBEBC'] }all(output(count()))",
            "all(group(mod(div(date,mul(60,60)),24)) each(output(sum(price))))",
            "all(group(customer) each(output(sum(mul(price,sub(1,tax))))))",
            "all( group(a) each(output(count())) )",
            "all( all(group(a) each(output(count()))) all(group(b) each(output(count()))) )",
            "all( max(1000) all(group(a) each(output(count()))) )",
            "all( group(a % 5) each(output(count())) )",
            "all( group(a + b * c) each(output(count())) )",
            "all( group(a % 5) order(sum(b)) each(output(count())) )",
            "all( group(a + b * c) order(max(d)) each(output(count())) )",
            "all( group(a) order(avg(relevance()) * count()) each(output(count())) )",
            "all(group(a) order(max(attr) * count()) each(output(count())) )",
            "all( group(a) each(max(1) each(output(summary()))) )",
            "all( group(a) each(max(1) output(count(), sum(b)) each(output(summary()))) )",
            "all(group(a) each(max(1) output(count(), sum(b), xor(md5(cat(a, b, c), 64))) each(output(summary()))))",
            "all( group(a) max(5) each(max(69) output(count()) each(output(summary()))) )",
            "all( group(a) max(5) each(output(count()) all(group(b) max(5) each(max(69) output(count()) each(output(summary()))))) )",
            "all( group(a) max(5) each(output(count()) all(group(b) max(5) each(output(count()) all(group(c) max(5) each(max(69) output(count()) each(output(summary()))))) )))",
            "all( group(a) max(5) each(output(count()) all(group(b) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(c) max(5) each(max(69) output(count()) each(output(summary()))))) )))",
            "all( group(a) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(b) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(c) max(5) each(max(69) output(count()) each(output(summary()))))) )))",
            "all( group(a) max(5) each(output(count()) all(max(1) each(output(summary(complexsummary)))) all(group(b) max(5) each(output(count()) all(max(1) each(output(summary(simplesummary)))) all(group(c) max(5) each(max(69) output(count()) each(output(summary(fastsummary)))))) )))",
            "all( group(a) max(5) each(output(count()) all(max(1) each(output(summary()))) all(group(b) each(output(count()) all(max(1) each(output(summary()))) all(group(c) each(output(count()) all(max(1) each(output(summary())))))))) )))",
            "all( group(time.year(a)) each(output(count())) )",
            "all( group(time.year(a)) each(output(count()) all(group(time.monthofyear(a)) each(output(count())))) )",
            "all( group(time.year(a)) each(output(count()) all(group(time.monthofyear(a)) each(output(count()) all(group(time.dayofmonth(a)) each(output(count()) all(group(time.hourofday(a)) each(output(count())))))))) )",
            "all( group(predefined((now() - a) / (60 * 60 * 24), bucket(0,1), bucket(1,2), bucket(3,7), bucket(8,31))) each(output(count()) all(max(2) each(output(summary()))) all(group((now() - a) / (60 * 60 * 24)) each(output(count()) all(max(2) each(output(summary())))))) )",
            "all( group(a) output(count()) )",
            "all( group(strlen(name)) output(count()) )",
            "all( group(a) output(count()) each(output(sum(b))) )",
            "all( group(a) max(3) output(count()) each(output(sum(b))) )",
            "all( group(a) max(10) output(count()) each(group(b) output(count())) )",
            "all(group(1) each(output(avg(rating))))",
            "all( group(predefined(rating, bucket[-inf, 0>, bucket[0, inf>)) each(output(count())) )",
            "all( group(predefined(rating, bucket[-inf, 0>, bucket[0, inf>)) order(max(rating)) max(1) each( max(100) each(output(summary(name_only)))) )",
        };

        for (int i = 0; i < groupingQueries.length; i++) {
            groupingQueries[i] = "select * from sources * where true | " + groupingQueries[i];
        }

        String[] queries = new String[] {
            "select * from music",
            "select * from sources * where range(title, 0.0, 500.0)", // /container-search/src/test/java/com/yahoo/select/SelectTestCase.java

            // From docs /en/query-language.html
            "select * from doc where true",
            "select * from doc where title contains \"ranking\"",
            "select * from doc where default contains \"ranking\"",
            "select * from doc where true order by term_count desc",
            "select * from doc where true limit 15 offset 5",
            "select * from doc where true limit 0 | all( group( fixedwidth(term_count,100) ) each( output( avg(term_count) ) ) )",
            "select * from doc where last_updated > 1646167144",
            "select * from doc where term_count = 1403",
            "select * from doc where default contains phrase(\"question\",\"answering\")",
            "select * from doc where true timeout 100",
            "select * from doc where namespace matches \"op-*\"",
            "select * from doc where is_public = true",
            "select * from doc where my_map contains sameElement(key contains \"Coldplay\", value > 10)",
            "select * from music where artist matches \"Meta..ica\"",

            // From docs /en/reference/query-language-reference.html
            "select * from sources * where text contains \"blues\"",
            "select price,isbn from sources * where title contains \"madonna\"",
            "select * from sources * where title contains \"madonna\"",
            "select * from music where title contains \"madonna\"",

            "select * from music where 500 >= price",
            "select * from music where range(fieldname, 0, 5000000000L)",
            "select * from music where (range(year, 2000, Infinity))",
            "select * from music where alive = true",
            "select * from music where title contains \"madonna\" and title contains \"saint\"",
            "select * from music where title contains \"madonna\" or title contains \"saint\"",
            "select * from music where title contains \"madonna\" and !(title contains \"saint\")",
            "select * from music where text contains phrase(\"st\", \"louis\", \"blues\")",
            "select * from music where persons contains sameElement(first_name contains 'Joe', last_name contains 'Smith', year_of_birth < 1940)",
            "select * from music where identities contains sameElement(key contains 'father', value.first_name contains 'Joe', value.last_name contains 'Smith', value.year_of_birth < 1940)",
            "select * from music where gradparentStruct.parentStruct.childField contains 'madonna'",
            "select * from music where fieldName contains equiv(\"A\",\"B\")",
            "select * from music where myUrlField contains uri(\"vespa.ai/foo\")",
            "select * from music where myStringAttribute contains ({prefixLength:1, maxEditDistance:2}fuzzy(\"parantesis\"))",
            "select * from music where attribute_field matches \"mado[n]+a\"",
            "select * from sources * where userInput(@animal)",
            "select * from sources * where weakAnd(default contains \"panda\")",
            "select * from sources * where vendor contains \"brick and mortar\" AND price < 50 AND userQuery()",
            "select * from music where rank(a contains \"A\", b contains \"B\", c contains \"C\")",
            "select * from music where rank(nearestNeighbor(field, queryVector), a contains \"A\", b contains \"B\", c contains \"C\")",
            "select * from music where integer_field in (10, 20, 30)",
            "select * from music where string_field in ('germany', 'france', 'norway')",
            "select * from music where integer_field in (@integer_values)",
            "select * from music where string_field in (@string_values)",
            "select * from music where dotProduct(description, {\"a\":1, \"b\":2})",
            "select * from music where weightedSet(description, {\"a\":1, \"b\":2})",
            "select * from music where wand(description, [[11,1], [37,2]])",
            "select * from music where ({scoreThreshold: 0.13, targetHits: 7}wand(description, {\"a\":1, \"b\":2}))",
            "select * from music where weakAnd(a contains \"A\", b contains \"B\")",
            "select * from music where ({targetHits: 7}weakAnd(a contains \"A\", b contains \"B\"))",
            "select * from music where geoLocation(myfieldname, 63.5, 10.5, \"200 km\")",
            "select * from music where ({targetHits: 10}nearestNeighbor(doc_vector, query_vector))",
            "select * from sources * where bar contains \"a\" and nonEmpty(bar contains \"bar\" and foo contains @foo)",
            "select * from music where predicate(predicate_field,{\"gender\":\"Female\"},{\"age\":20L})",
            "select * from music where predicate(predicate_field,0,{\"age\":20L})",
            "select * from music where title contains \"madonna\" order by price asc, releasedate desc",
            "select * from music where title contains \"madonna\" order by {function: \"uca\", locale: \"en_US\", strength: \"IDENTICAL\"}other desc, {function: \"lowercase\"}something",
            "select * from music where title contains \"madonna\" limit 31 offset 29",
            "select * from music where title contains \"madonna\" timeout 70",
            "select * from music where userInput(@userinput)",
            "select * from music where text contains ({distance: 5}near(\"a\", \"b\")) and text contains ({distance:2}near(\"c\", \"d\"))",
            "select * from music where ({bounds:\"rightOpen\"}range(year, 2000, 2018))",
            "select * from music where text contains ({distance: 5}near(\"a\", \"b\"))",
            "select * from music where myUrlField.hostname contains uri(\"vespa.ai\")",
            "select * from music where myUrlField.hostname contains ({startAnchor: true}uri(\"vespa.ai\"))",
            "select * from music where title contains ({weight:200}\"heads\")",
            "select * from sources * where ({stem: false}(foo contains \"a\" and bar contains \"b\")) or foo contains {stem: false}\"c\"",
            "select * from sources * where foo contains @animal and foo contains phrase(@animal, @syntaxExample, @animal)",
            "select * from sources * where sddocname contains 'purchase' | all(group(customer) each(output(sum(price))))",
        };

        Stream<String> queryStream = Stream.concat(Arrays.stream(queries), Arrays.stream(groupingQueries));

        return queryStream.map(query -> DynamicTest.dynamicTest(query, () -> checkQueryParses(0, query)));
    }

    private record TestWithError(int expectedErrors, String query) {}

    @TestFactory
    Stream<DynamicTest> InvalidQuery() throws Exception {
        var queries = new TestWithError[] {
            new TestWithError(1, "seletc *"),
            // new TestWithError(1, "select * from sources * where true | all(group(a) order(attr * count()) each(output(count())) )"),
        };

        return Arrays.stream(queries)
                     .map(queryTest -> DynamicTest.dynamicTest(
                        queryTest.query() + " Expects " + queryTest.expectedErrors() + " errors",
                        () -> checkQueryParses(queryTest.expectedErrors(), queryTest.query())
                      ));
    }


}
